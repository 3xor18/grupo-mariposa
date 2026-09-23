import { NestExpressApplication } from '@nestjs/platform-express';
import request from 'supertest';
import { App } from 'supertest/types';
import { AddressInfo } from 'node:net';
import { ErrorCode } from '../src/shared/errors/error-code.enum';
import { DATABASE_HEALTH } from '../src/shared/mongo/mongo.tokens';
import { SHUTTING_DOWN_DETAIL } from '../src/shared/fault-injection/fault-injection.interceptor';
import { TestIdentityProvider } from './support/identity-provider';
import { createTestApp, NO_AUTH, testConfig } from './support/test-app';

describe('platform endpoints', () => {
  let idp: TestIdentityProvider;
  let app: NestExpressApplication;
  let server: App;

  beforeAll(async () => {
    idp = await TestIdentityProvider.start();
    app = await createTestApp(testConfig(idp.jwksUrl));
    server = app.getHttpServer();
  });

  afterAll(async () => {
    await app.close();
    await idp.stop();
  });

  it('should_report_liveness_without_token', async () => {
    const response = await request(server).get('/health/live').expect(200);

    expect(response.body).toEqual({ status: 'UP' });
  });

  it('should_report_readiness_without_token', async () => {
    const response = await request(server).get('/health/ready').expect(200);

    expect(response.body).toEqual({ status: 'UP' });
  });

  it('should_expose_prometheus_metrics_by_route_and_status', async () => {
    await request(server).get('/clients/CLI-99821').expect(401);
    await request(server).get('/missing').expect(404);

    const response = await request(server).get('/metrics').expect(200);

    expect(response.headers['content-type']).toContain('text/plain');
    expect(response.text).toContain(
      'http_requests_total{method="GET",route="/clients/:clientId",status_code="401"} 1',
    );
    expect(response.text).toContain('route="UNMATCHED",status_code="404"');
    expect(response.text).toContain('http_request_duration_seconds_bucket');
  });

  it('should_serve_openapi_document_at_docs', async () => {
    const response = await request(server).get('/docs-json').expect(200);

    expect(response.body).toMatchObject({ openapi: '3.1.0', info: { title: 'Clients API' } });
  });
});

describe('graceful shutdown through app.close()', () => {
  it('should_drain_with_readiness_down_cancel_held_requests_and_close_connections', async () => {
    const config = testConfig('http://unused');
    const app = await createTestApp({
      ...config,
      auth: NO_AUTH,
      faultInjection: { ...config.faultInjection, timeoutMs: 30_000 },
      shutdown: { drainMs: 400, timeoutMs: 1000 },
    });
    await app.listen(0, '127.0.0.1');
    const { port } = app.getHttpServer().address() as AddressInfo;
    const base = `http://127.0.0.1:${String(port)}`;
    await expect(fetch(`${base}/health/ready`)).resolves.toMatchObject({ status: 200 });
    const held = fetch(`${base}/clients/CLI-SLOW`);
    await new Promise((resolve) => setTimeout(resolve, 50));
    const startedAt = Date.now();

    const closing = app.close();
    const heldResponse = await held;
    const readiness = await fetch(`${base}/health/ready`);
    await closing;

    expect(heldResponse.status).toBe(503);
    expect(await heldResponse.json()).toMatchObject({
      code: ErrorCode.SERVICE_UNAVAILABLE,
      detail: SHUTTING_DOWN_DETAIL,
    });
    expect(readiness.status).toBe(503);
    expect(await readiness.json()).toEqual({ status: 'DOWN' });
    expect(Date.now() - startedAt).toBeGreaterThanOrEqual(390);
    expect(Date.now() - startedAt).toBeLessThan(5000);
    await expect(fetch(`${base}/health/live`)).rejects.toThrow('fetch failed');
  });
});

describe('api docs gate', () => {
  it('should_not_serve_swagger_when_api_docs_are_disabled', async () => {
    const config = testConfig('http://unused');
    const app = await createTestApp({
      ...config,
      auth: NO_AUTH,
      http: { ...config.http, apiDocsEnabled: false },
    });
    const server = app.getHttpServer() as App;

    const json = await request(server).get('/docs-json').expect(404);
    await request(server).get('/docs').expect(404);

    expect(json.body).toMatchObject({ code: ErrorCode.NOT_FOUND });
    await app.close();
  });
});

describe('readiness with an unavailable database', () => {
  it('should_report_down_when_the_database_ping_fails', async () => {
    const app = await createTestApp(
      { ...testConfig('http://unused'), auth: NO_AUTH },
      { token: DATABASE_HEALTH, value: { isHealthy: () => Promise.resolve(false) } },
    );
    const server = app.getHttpServer() as App;

    const response = await request(server).get('/health/ready').expect(503);
    await request(server).get('/health/live').expect(200);

    expect(response.body).toEqual({ status: 'DOWN' });
    await app.close();
  });
});
