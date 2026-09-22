import { NestExpressApplication } from '@nestjs/platform-express';
import { request as httpRequest } from 'node:http';
import { AddressInfo } from 'node:net';
import request from 'supertest';
import { App } from 'supertest/types';
import { GetClientUseCase } from '../src/clients/application/get-client.use-case';
import { ErrorCode } from '../src/shared/errors/error-code.enum';
import { createTestApp, testConfig } from './support/test-app';

const noAuth = { enabled: false } as const;

describe('fault injection', () => {
  let app: NestExpressApplication;
  let server: App;

  beforeAll(async () => {
    app = await createTestApp({ ...testConfig('http://unused'), auth: noAuth });
    server = app.getHttpServer();
  });

  afterAll(async () => {
    await app.close();
  });

  it('should_fail_first_two_requests_then_recover_when_rule_has_times', async () => {
    const first = await request(server).get('/clients/CLI-40001').expect(503);
    await request(server).get('/clients/CLI-40001').expect(503);
    await request(server).get('/clients/CLI-40001').expect(200);

    expect(first.body).toMatchObject({ code: ErrorCode.SERVICE_UNAVAILABLE, status: 503 });
  });

  it('should_always_fail_when_rule_has_no_times', async () => {
    for (let attempt = 0; attempt < 3; attempt += 1) {
      await request(server).get('/clients/CLI-40002').expect(503);
    }
  });

  it.each([
    ['CLI-FAULT400', 400, ErrorCode.VALIDATION_ERROR],
    ['CLI-FAULT429', 429, ErrorCode.RATE_LIMITED],
    ['CLI-FAULT500', 500, ErrorCode.INTERNAL_ERROR],
    ['CLI-FAULT502', 502, ErrorCode.BAD_GATEWAY],
  ])('should_inject_%s_as_status_%i', async (clientId, status, code) => {
    const response = await request(server).get(`/clients/${clientId}`).expect(status);

    expect(response.body).toMatchObject({ status, code });
  });

  it('should_send_retry_after_when_injecting_429', async () => {
    const response = await request(server).get('/clients/CLI-FAULT429').expect(429);

    expect(response.headers['retry-after']).toBe('1');
  });

  it('should_hold_response_for_timeout_then_answer_normally', async () => {
    const startedAt = Date.now();

    await request(server).get('/clients/CLI-SLOW').expect(404);

    expect(Date.now() - startedAt).toBeGreaterThanOrEqual(90);
  });

  it('should_not_affect_clients_without_rules', async () => {
    await request(server).get('/clients/CLI-99821').expect(200);
  });
});

describe('fault injection timeout aborted by the client', () => {
  let app: NestExpressApplication;

  beforeAll(async () => {
    const config = testConfig('http://unused');
    app = await createTestApp({
      ...config,
      auth: noAuth,
      faultInjection: { ...config.faultInjection, timeoutMs: 10_000 },
    });
    await app.listen(0, '127.0.0.1');
  });

  afterAll(async () => {
    await app.close();
  });

  it('should_stop_holding_and_skip_handler_when_client_cancels', async () => {
    const execute = jest.spyOn(app.get(GetClientUseCase), 'execute');
    const { port } = app.getHttpServer().address() as AddressInfo;
    const startedAt = Date.now();

    await new Promise<void>((resolve) => {
      const outgoing = httpRequest({ host: '127.0.0.1', port, path: '/clients/CLI-SLOW' });
      outgoing.on('error', () => {
        resolve();
      });
      outgoing.end();
      setTimeout(() => {
        outgoing.destroy();
      }, 50);
    });
    await new Promise((resolve) => setTimeout(resolve, 50));

    expect(Date.now() - startedAt).toBeLessThan(5_000);
    expect(execute).not.toHaveBeenCalled();
  });
});

describe('rate limiting', () => {
  let app: NestExpressApplication;
  let server: App;

  beforeAll(async () => {
    app = await createTestApp({
      ...testConfig('http://unused'),
      auth: noAuth,
      rateLimit: { requestsPerSecond: 0.001, burst: 2 },
    });
    server = app.getHttpServer();
  });

  afterAll(async () => {
    await app.close();
  });

  it('should_return_429_with_retry_after_when_burst_is_exhausted', async () => {
    await request(server).get('/clients/CLI-99821').expect(200);
    await request(server).get('/clients/CLI-99821').expect(200);

    const response = await request(server).get('/clients/CLI-99821').expect(429);

    expect(response.body).toMatchObject({ status: 429, code: ErrorCode.RATE_LIMITED });
    expect(Number(response.headers['retry-after'])).toBeGreaterThanOrEqual(1);
  });

  it('should_keep_health_probes_available_when_rate_limited', async () => {
    await request(server).get('/health/live').expect(200);
  });
});
