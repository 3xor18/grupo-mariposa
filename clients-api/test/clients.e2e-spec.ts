import { NestExpressApplication } from '@nestjs/platform-express';
import request from 'supertest';
import { App } from 'supertest/types';
import { CLIENT_REPOSITORY } from '../src/clients/application/client.repository';
import { ClientStatus } from '../src/clients/domain/client-status.enum';
import { Market } from '../src/clients/domain/market.enum';
import { Segment } from '../src/clients/domain/segment.enum';
import { TaxRegime } from '../src/clients/domain/tax-regime.enum';
import { ErrorCode } from '../src/shared/errors/error-code.enum';
import { PROBLEM_CONTENT_TYPE } from '../src/shared/errors/problem-details';
import { TestIdentityProvider } from './support/identity-provider';
import { createTestApp, NO_AUTH, testConfig } from './support/test-app';

const TRACE_ID = '0af7651916cd43dd8448eb211c80319c';
const TRACEPARENT = `00-${TRACE_ID}-b7ad6b7169203331-01`;

describe('GET /clients/:clientId', () => {
  let idp: TestIdentityProvider;
  let app: NestExpressApplication;
  let server: App;
  let bearer: string;

  beforeAll(async () => {
    idp = await TestIdentityProvider.start();
    app = await createTestApp(testConfig(idp.jwksUrl));
    server = app.getHttpServer();
    bearer = `Bearer ${await idp.token()}`;
  });

  afterAll(async () => {
    await app.close();
    await idp.stop();
  });

  const get = (path: string): request.Test =>
    request(server).get(path).set('Authorization', bearer);

  it('should_return_client_when_it_exists', async () => {
    const response = await get('/clients/CLI-99821').expect(200);

    expect(response.headers['content-type']).toMatch(/^application\/json/);
    expect(response.body).toEqual({
      clientId: 'CLI-99821',
      name: 'Distribuidora Central',
      status: ClientStatus.ACTIVE,
      segment: Segment.WHOLESALE,
      taxRegime: TaxRegime.GENERAL,
      market: Market.MX,
    });
  });

  it('should_return_blocked_client_when_seed_marks_it_blocked', async () => {
    const response = await get('/clients/CLI-20002').expect(200);

    expect(response.body).toMatchObject({ status: ClientStatus.BLOCKED, market: Market.CO });
  });

  it.each(['cli-99821', 'CLI-', 'CLI-abc', `CLI-${'9'.repeat(21)}`, 'XYZ-1'])(
    'should_return_400_validation_error_when_client_id_is_%s',
    async (clientId) => {
      const response = await get(`/clients/${clientId}`).expect(400);

      expect(response.headers['content-type']).toContain(PROBLEM_CONTENT_TYPE);
      expect(response.body).toMatchObject({
        status: 400,
        code: ErrorCode.VALIDATION_ERROR,
        instance: `/clients/${clientId}`,
        detail: 'The request contains invalid parameters',
        errors: [{ field: 'clientId', message: 'clientId must match ^CLI-[A-Z0-9]{1,20}$' }],
      });
    },
  );

  it('should_return_404_client_not_found_when_client_does_not_exist', async () => {
    const response = await get('/clients/CLI-00000').set('traceparent', TRACEPARENT).expect(404);

    expect(response.body).toMatchObject({
      type: 'https://contracts.grupomariposa.dev/problems/client-not-found',
      title: 'Client not found',
      status: 404,
      code: ErrorCode.CLIENT_NOT_FOUND,
      detail: 'Client CLI-00000 does not exist',
      instance: '/clients/CLI-00000',
      traceId: TRACE_ID,
    });
    expect(response.headers['x-request-id']).toBe(TRACE_ID);
  });

  it('should_echo_request_id_but_keep_a_w3c_trace_id_when_only_request_id_is_sent', async () => {
    const response = await get('/clients/CLI-00000').set('X-Request-Id', 'req-123').expect(404);
    const body = response.body as { traceId: string };

    expect(response.headers['x-request-id']).toBe('req-123');
    expect(body.traceId).toMatch(/^[\da-f]{32}$/);
  });

  it('should_return_401_when_token_is_missing', async () => {
    const response = await request(server).get('/clients/CLI-99821').expect(401);

    expect(response.body).toMatchObject({
      status: 401,
      code: ErrorCode.UNAUTHORIZED,
      detail: 'A bearer token is required',
    });
  });

  it('should_return_401_when_token_is_expired', async () => {
    const token = await idp.token({ expired: true });

    const response = await request(server)
      .get('/clients/CLI-99821')
      .set('Authorization', `Bearer ${token}`)
      .expect(401);

    expect(response.body).toMatchObject({ code: ErrorCode.UNAUTHORIZED });
  });

  it('should_return_401_when_token_has_another_issuer', async () => {
    const token = await idp.token({ issuer: 'http://evil.example/realms/mariposa' });

    const response = await request(server)
      .get('/clients/CLI-99821')
      .set('Authorization', `Bearer ${token}`)
      .expect(401);

    expect(response.body).toMatchObject({ code: ErrorCode.UNAUTHORIZED });
  });

  it('should_return_401_when_token_is_signed_by_unknown_key', async () => {
    const token = await idp.token({ key: await TestIdentityProvider.foreignKey() });

    const response = await request(server)
      .get('/clients/CLI-99821')
      .set('Authorization', `Bearer ${token}`)
      .expect(401);

    expect(response.body).toMatchObject({ code: ErrorCode.UNAUTHORIZED });
  });

  it('should_return_403_when_token_lacks_required_role', async () => {
    const token = await idp.token({ roles: ['products-reader'] });

    const response = await request(server)
      .get('/clients/CLI-99821')
      .set('Authorization', `Bearer ${token}`)
      .expect(403);

    expect(response.body).toMatchObject({
      status: 403,
      code: ErrorCode.FORBIDDEN,
      detail: 'The token does not grant the required role',
    });
  });

  it('should_return_401_when_token_targets_another_audience', async () => {
    const token = await idp.token({ audience: 'products-api' });

    const response = await request(server)
      .get('/clients/CLI-99821')
      .set('Authorization', `Bearer ${token}`)
      .expect(401);

    expect(response.body).toMatchObject({
      code: ErrorCode.UNAUTHORIZED,
      detail: 'The bearer token is invalid or expired',
    });
  });

  it('should_accept_lowercase_bearer_scheme', async () => {
    const response = await request(server)
      .get('/clients/CLI-99821')
      .set('Authorization', `bearer ${await idp.token()}`)
      .expect(200);

    expect(response.body).toMatchObject({ clientId: 'CLI-99821' });
  });

  it('should_return_401_with_malformed_detail_when_scheme_is_not_bearer', async () => {
    const response = await request(server)
      .get('/clients/CLI-99821')
      .set('Authorization', 'Basic dXNlcjpwYXNz')
      .expect(401);

    expect(response.body).toMatchObject({
      detail: 'The authorization header must be "Bearer <jwt>"',
    });
  });

  it('should_return_problem_for_unknown_route', async () => {
    const response = await get('/unknown').expect(404);

    expect(response.body).toMatchObject({
      code: ErrorCode.NOT_FOUND,
      instance: '/unknown',
      detail: 'Cannot GET /unknown',
    });
  });
});

describe('GET /clients/:clientId with authentication disabled', () => {
  let app: NestExpressApplication;

  beforeAll(async () => {
    app = await createTestApp({ ...testConfig('http://unused'), auth: NO_AUTH });
  });

  afterAll(async () => {
    await app.close();
  });

  it('should_return_client_without_token_when_auth_is_disabled', async () => {
    const response = await request(app.getHttpServer() as App)
      .get('/clients/CLI-30002')
      .expect(200);

    expect(response.body).toMatchObject({ clientId: 'CLI-30002', market: 'PE' });
  });
});

describe('GET /clients/:clientId when the repository fails unexpectedly', () => {
  let app: NestExpressApplication;

  beforeAll(async () => {
    const failingRepository = { findById: () => Promise.reject(new Error('database is down')) };
    app = await createTestApp(
      { ...testConfig('http://unused'), auth: NO_AUTH },
      { token: CLIENT_REPOSITORY, value: failingRepository },
    );
  });

  afterAll(async () => {
    await app.close();
  });

  it('should_return_500_internal_error_without_leaking_details', async () => {
    const response = await request(app.getHttpServer() as App)
      .get('/clients/CLI-99821')
      .expect(500);

    expect(response.body).toMatchObject({
      status: 500,
      code: ErrorCode.INTERNAL_ERROR,
      detail: 'An unexpected error occurred',
    });
    expect(JSON.stringify(response.body)).not.toContain('database is down');
  });
});
