import { NestExpressApplication } from '@nestjs/platform-express';
import { OpenAPIObject } from '@nestjs/swagger';
import request from 'supertest';
import { App } from 'supertest/types';
import { CLIENT_SEED } from '../src/clients/infrastructure/client.seed';
import { createOpenApiDocument } from '../src/swagger';
import {
  ContractValidators,
  createContractValidators,
  loadClientsContract,
} from './support/contracts';
import { TestIdentityProvider } from './support/identity-provider';
import { createTestApp, testConfig } from './support/test-app';

const byText = (left: string, right: string): number => left.localeCompare(right);
const HTTP_METHODS = ['get', 'post', 'put', 'patch', 'delete'] as const;

interface GeneratedOperation {
  readonly operationId?: string;
  readonly responses: Record<string, unknown>;
}

describe('contract compliance', () => {
  let idp: TestIdentityProvider;
  let app: NestExpressApplication;
  let server: App;
  let validators: ContractValidators;
  let bearer: string;

  beforeAll(async () => {
    idp = await TestIdentityProvider.start();
    app = await createTestApp(
      testConfig(idp.jwksUrl, {
        rateLimit: { requestsPerSecond: 1, burst: 60, maxTrackedCallers: 10 },
      }),
    );
    server = app.getHttpServer();
    validators = createContractValidators();
    bearer = `Bearer ${await idp.token()}`;
  });

  afterAll(async () => {
    await app.close();
    await idp.stop();
  });

  it.each(CLIENT_SEED.map((client) => client.id).filter((id) => !id.startsWith('CLI-4')))(
    'should_match_client_schema_for_%s',
    async (clientId) => {
      const response = await request(server)
        .get(`/clients/${clientId}`)
        .set('Authorization', bearer)
        .expect(200);

      expect(validators.client(response.body)).toBe(true);
    },
  );

  it.each([
    ['/clients/bad-id', true, 400],
    ['/clients/CLI-99821', false, 401],
    ['/clients/CLI-00000', true, 404],
    ['/clients/CLI-40002', true, 503],
    ['/clients/CLI-FAULT429', true, 429],
    ['/clients/CLI-FAULT500', true, 500],
    ['/clients/CLI-FAULT502', true, 502],
  ])('should_match_problem_schema_for_%s', async (path, authenticated, status) => {
    const call = request(server).get(path);
    const response = await (authenticated ? call.set('Authorization', bearer) : call).expect(
      status,
    );

    expect(response.headers['content-type']).toContain('application/problem+json');
    expect(validators.problem(response.body)).toBe(true);
    expect(validators.problem.errors ?? []).toEqual([]);
  });

  it('should_match_problem_schema_for_403', async () => {
    const token = await idp.token({ roles: [] });

    const response = await request(server)
      .get('/clients/CLI-99821')
      .set('Authorization', `Bearer ${token}`)
      .expect(403);

    expect(validators.problem(response.body)).toBe(true);
  });

  it.each(['/health/live', '/health/ready'])('should_match_health_schema_for_%s', async (path) => {
    const response = await request(server).get(path).expect(200);

    expect(validators.health(response.body)).toBe(true);
  });

  it('should_publish_the_same_operations_and_statuses_as_the_contract', () => {
    const contract = loadClientsContract();
    const generated = createOpenApiDocument(app) as OpenAPIObject & {
      paths: Record<string, Record<string, GeneratedOperation>>;
    };

    for (const [path, operations] of Object.entries(contract.paths)) {
      for (const method of HTTP_METHODS.filter((candidate) => candidate in operations)) {
        const expected = operations[method];
        const actual = generated.paths[path]?.[method];

        expect(actual?.operationId).toBe(expected?.operationId);
        expect(Object.keys(actual?.responses ?? {}).sort(byText)).toEqual(
          Object.keys(expected?.responses ?? {}).sort(byText),
        );
      }
    }
  });

  it('should_publish_the_contract_client_schema_properties_and_enums', () => {
    const contract = loadClientsContract();
    const generated = createOpenApiDocument(app);
    const expected = contract.components.schemas.Client as {
      required: string[];
      properties: Record<string, { enum?: string[] }>;
    };
    const actual = generated.components?.schemas?.Client as {
      required: string[];
      properties: Record<string, { enum?: string[] }>;
    };

    expect([...actual.required].sort(byText)).toEqual([...expected.required].sort(byText));
    for (const [name, property] of Object.entries(expected.properties)) {
      expect(actual.properties[name]?.enum).toEqual(property.enum);
    }
  });
});
