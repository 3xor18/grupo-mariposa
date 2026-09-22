import { NestExpressApplication } from '@nestjs/platform-express';
import request from 'supertest';
import { App } from 'supertest/types';
import { ReadinessState } from '../src/health/readiness.state';
import { TestIdentityProvider } from './support/identity-provider';
import { createTestApp, testConfig } from './support/test-app';

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

describe('graceful shutdown', () => {
  it('should_report_readiness_down_once_shutdown_starts', async () => {
    const idp = await TestIdentityProvider.start();
    const app = await createTestApp(testConfig(idp.jwksUrl));
    const server = app.getHttpServer() as App;

    app.get(ReadinessState).beforeApplicationShutdown();
    const response = await request(server).get('/health/ready').expect(503);

    expect(response.body).toEqual({ status: 'DOWN' });
    await request(server).get('/health/live').expect(200);
    await app.close();
    await idp.stop();
  });
});
