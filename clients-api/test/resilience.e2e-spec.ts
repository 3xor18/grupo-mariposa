import { NestExpressApplication } from '@nestjs/platform-express';
import { request as httpRequest } from 'node:http';
import { AddressInfo } from 'node:net';
import request from 'supertest';
import { App } from 'supertest/types';
import { GetClientUseCase } from '../src/clients/application/get-client.use-case';
import { AppConfig } from '../src/config/app-config';
import { ErrorCode } from '../src/shared/errors/error-code.enum';
import { KeyedRateLimiter } from '../src/shared/rate-limit/keyed-rate-limiter';
import { CLIENT_ADDRESS_RATE_LIMITER } from '../src/shared/rate-limit/rate-limit.guard';
import { TestIdentityProvider } from './support/identity-provider';
import { createTestApp, NO_AUTH, testConfig } from './support/test-app';

const unauthenticated = (overrides: Partial<AppConfig> = {}): AppConfig => ({
  ...testConfig('http://unused'),
  auth: NO_AUTH,
  ...overrides,
});

describe('fault injection', () => {
  let app: NestExpressApplication;
  let server: App;

  beforeEach(async () => {
    app = await createTestApp(unauthenticated());
    server = app.getHttpServer();
  });

  afterEach(async () => {
    await app.close();
  });

  it('should_fail_first_two_requests_then_recover_when_rule_has_times', async () => {
    const statuses: number[] = [];
    for (let attempt = 0; attempt < 4; attempt += 1) {
      statuses.push((await request(server).get('/clients/CLI-40001')).status);
    }

    expect(statuses).toEqual([503, 503, 200, 200]);
  });

  it('should_always_fail_when_rule_has_no_times', async () => {
    const statuses: number[] = [];
    for (let attempt = 0; attempt < 3; attempt += 1) {
      statuses.push((await request(server).get('/clients/CLI-40002')).status);
    }

    expect(statuses).toEqual([503, 503, 503]);
  });

  it.each([
    ['CLI-FAULT400', 400, ErrorCode.VALIDATION_ERROR],
    ['CLI-FAULT429', 429, ErrorCode.RATE_LIMITED],
    ['CLI-FAULT500', 500, ErrorCode.INTERNAL_ERROR],
    ['CLI-FAULT502', 502, ErrorCode.BAD_GATEWAY],
    ['CLI-40001', 503, ErrorCode.SERVICE_UNAVAILABLE],
  ])('should_inject_%s_as_status_%i', async (clientId, status, code) => {
    const response = await request(server).get(`/clients/${clientId}`).expect(status);

    expect(response.body).toMatchObject({ status, code, detail: 'Injected fault' });
  });

  it('should_send_retry_after_when_injecting_429', async () => {
    const response = await request(server).get('/clients/CLI-FAULT429').expect(429);

    expect(response.headers['retry-after']).toBe('1');
  });

  it('should_hold_response_for_timeout_then_answer_normally', async () => {
    const startedAt = Date.now();

    const response = await request(server).get('/clients/CLI-SLOW').expect(404);

    expect(Date.now() - startedAt).toBeGreaterThanOrEqual(95);
    expect(response.body).toMatchObject({ code: ErrorCode.CLIENT_NOT_FOUND });
  });

  it('should_not_affect_clients_without_rules', async () => {
    const response = await request(server).get('/clients/CLI-99821').expect(200);

    expect(response.body).toMatchObject({ clientId: 'CLI-99821' });
  });
});

describe('fault injection disabled', () => {
  it('should_serve_demo_fixtures_normally_when_fault_injection_is_off', async () => {
    const config = unauthenticated();
    const app = await createTestApp({
      ...config,
      faultInjection: { ...config.faultInjection, enabled: false },
    });

    const response = await request(app.getHttpServer() as App)
      .get('/clients/CLI-40002')
      .expect(200);

    expect(response.body).toMatchObject({ clientId: 'CLI-40002', name: 'Comercial Intermitente' });
    await app.close();
  });
});

describe('fault injection timeout aborted by the client', () => {
  let app: NestExpressApplication;

  beforeAll(async () => {
    const config = unauthenticated();
    app = await createTestApp({
      ...config,
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

    const outcome = await new Promise<string>((resolve) => {
      const outgoing = httpRequest({ host: '127.0.0.1', port, path: '/clients/CLI-SLOW' });
      outgoing.on('response', () => {
        resolve('response');
      });
      outgoing.on('error', (error) => {
        resolve(error.message);
      });
      outgoing.end();
      setTimeout(() => {
        outgoing.destroy();
      }, 50);
    });
    await new Promise((resolve) => setTimeout(resolve, 50));

    expect(outcome).toBe('socket hang up');
    expect(Date.now() - startedAt).toBeLessThan(1000);
    expect(execute).not.toHaveBeenCalled();
  });
});

describe('rate limiting per client address', () => {
  let app: NestExpressApplication;
  let server: App;

  beforeAll(async () => {
    app = await createTestApp(
      unauthenticated({ rateLimit: { requestsPerSecond: 0.001, burst: 2, maxTrackedCallers: 10 } }),
    );
    server = app.getHttpServer();
  });

  afterAll(async () => {
    await app.close();
  });

  it('should_return_429_with_retry_after_when_the_address_bucket_is_exhausted', async () => {
    await request(server).get('/clients/CLI-99821').expect(200);
    await request(server).get('/clients/CLI-99821').expect(200);

    const response = await request(server).get('/clients/CLI-99821').expect(429);

    expect(response.body).toMatchObject({
      status: 429,
      code: ErrorCode.RATE_LIMITED,
      detail: 'Request rate limit exceeded, retry later',
    });
    expect(response.headers['retry-after']).toBe('1000');
  });

  it('should_keep_health_probes_available_when_rate_limited', async () => {
    const response = await request(server).get('/health/live').expect(200);

    expect(response.body).toEqual({ status: 'UP' });
  });
});

describe('rate limiting per authenticated principal', () => {
  let idp: TestIdentityProvider;
  let app: NestExpressApplication;
  let server: App;

  beforeAll(async () => {
    idp = await TestIdentityProvider.start();
    app = await createTestApp(
      testConfig(idp.jwksUrl, {
        rateLimit: { requestsPerSecond: 0.001, burst: 2, maxTrackedCallers: 10 },
      }),
      {
        token: CLIENT_ADDRESS_RATE_LIMITER,
        value: new KeyedRateLimiter({ requestsPerSecond: 1000, burst: 1000, maxTrackedCallers: 1 }),
      },
    );
    server = app.getHttpServer();
  });

  afterAll(async () => {
    await app.close();
    await idp.stop();
  });

  it('should_throttle_one_principal_without_affecting_another_from_the_same_address', async () => {
    const noisy = `Bearer ${await idp.token({ claims: { azp: 'noisy-client' } })}`;
    const quiet = `Bearer ${await idp.token({ claims: { azp: 'quiet-client' } })}`;
    const call = (bearer: string): request.Test =>
      request(server).get('/clients/CLI-99821').set('Authorization', bearer);

    const statuses = [
      (await call(noisy)).status,
      (await call(noisy)).status,
      (await call(noisy)).status,
      (await call(quiet)).status,
    ];

    expect(statuses).toEqual([200, 200, 429, 200]);
  });

  it('should_reject_unauthenticated_calls_before_consuming_principal_tokens', async () => {
    const response = await request(server).get('/clients/CLI-99821').expect(401);

    expect(response.body).toMatchObject({ code: ErrorCode.UNAUTHORIZED });
  });
});

describe('rate limiting behind a trusted proxy', () => {
  const limitedBy = async (trustProxy: boolean | number): Promise<number[]> => {
    const config = unauthenticated({
      rateLimit: { requestsPerSecond: 0.001, burst: 1, maxTrackedCallers: 10 },
    });
    const app = await createTestApp({ ...config, http: { ...config.http, trustProxy } });
    const server = app.getHttpServer() as App;
    const statuses: number[] = [];
    for (const forwardedFor of ['203.0.113.10', '203.0.113.11']) {
      const response = await request(server)
        .get('/clients/CLI-99821')
        .set('X-Forwarded-For', forwardedFor);
      statuses.push(response.status);
    }
    await app.close();
    return statuses;
  };

  it('should_bucket_by_forwarded_client_address_when_proxy_is_trusted', async () => {
    await expect(limitedBy(1)).resolves.toEqual([200, 200]);
  });

  it('should_ignore_forwarded_header_when_proxy_is_not_trusted', async () => {
    await expect(limitedBy(false)).resolves.toEqual([200, 429]);
  });
});
