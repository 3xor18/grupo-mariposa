# clients-api

Distributor master data service (NestJS 11, TypeScript strict). Contract:
[`contracts/http/clients-api.openapi.yaml`](../contracts/http/clients-api.openapi.yaml); errors follow
[`contracts/common/problem.schema.json`](../contracts/common/problem.schema.json) (ADR 0004).

## Run

```bash
npm ci
npm run build
AUTH_ENABLED=false npm start            # http://localhost:3000
```

Docker (host port 8082 per `docs/platform-conventions.md`):

```bash
docker build -t clients-api .
docker run --rm -p 8082:3000 \
  -e AUTH_ISSUER=http://localhost:8180/realms/mariposa \
  -e AUTH_JWKS_URL=http://keycloak:8080/realms/mariposa/protocol/openid-connect/certs \
  -e FAULT_RULES=CLI-40001:503:2,CLI-40002:503 clients-api
```

| Endpoint                                | Auth                          | Notes                                                              |
| --------------------------------------- | ----------------------------- | ------------------------------------------------------------------ |
| `GET /clients/{clientId}`               | Bearer, role `clients-reader` | `clientId` must match `^CLI-[A-Z0-9]{1,20}$`                       |
| `GET /health/live`, `GET /health/ready` | public                        | `{"status":"UP"}`; readiness is `503 DOWN` once shutdown starts    |
| `GET /metrics`                          | public                        | Prometheus: `http_requests_total`, `http_request_duration_seconds` |
| `GET /docs`, `GET /docs-json`           | public                        | Swagger UI / generated OpenAPI 3.1                                 |

## Test

```bash
npm run lint          # ESLint (typescript-eslint strict + sonarjs) and Prettier
npm run typecheck
npm test              # unit (src/**/*.spec.ts) + e2e/contract (test/*.e2e-spec.ts)
npm run test:cov      # fails below 100% lines/branches/functions/statements (main.ts excluded)
```

Contract tests load `../contracts` and validate real responses with Ajv, and compare the generated
OpenAPI document (operations, status codes, `Client` schema and enums) against the contract. JWT tests
sign tokens with a locally generated RSA key served from an in-process JWKS endpoint.

## Environment

The configuration is validated at startup; the process exits with a `fatal` log on any invalid value.

| Variable             | Default                  | Description                                                                |
| -------------------- | ------------------------ | -------------------------------------------------------------------------- |
| `PORT`               | `3000`                   | HTTP port                                                                  |
| `LOG_LEVEL`          | `info`                   | `fatal`, `error`, `warn`, `info`, `debug`, `trace`, `silent`               |
| `AUTH_ENABLED`       | `true`                   | `false` only for local tests                                               |
| `AUTH_ISSUER`        | required when auth is on | expected `iss` claim                                                       |
| `AUTH_JWKS_URL`      | required when auth is on | Keycloak JWKS endpoint (RS256 only)                                        |
| `AUTH_REQUIRED_ROLE` | `clients-reader`         | role expected in `realm_access.roles`                                      |
| `FAULT_RULES`        | empty                    | `id:type[:times]` list, types `429`, `500`, `502`, `503`, `400`, `timeout` |
| `FAULT_TIMEOUT_MS`   | `5000`                   | hold time of `timeout` rules (released early if the client cancels)        |
| `RATE_LIMIT_RPS`     | `200`                    | token bucket refill rate per process                                       |
| `RATE_LIMIT_BURST`   | `400`                    | token bucket capacity; excess gets `429 RATE_LIMITED` + `Retry-After`      |

## Module design

```
src/
├── clients/                       feature module
│   ├── domain/                    Client, ClientStatus, Segment, TaxRegime, Market, ClientNotFoundError
│   ├── application/               GetClientUseCase + ClientRepository port (CLIENT_REPOSITORY token)
│   ├── infrastructure/            InMemoryClientRepository, CLIENT_SEED, provider binding
│   └── http/                      thin controller, GetClientParams (validation), ClientResponse, mapper
├── health/                        liveness/readiness, readiness flips to DOWN on shutdown
├── config/                        typed AppConfig loaded from env with zod (fail fast)
└── shared/
    ├── errors/                    ErrorCode catalog, ProblemException, DomainError, global filter
    ├── auth/                      global JwtAuthGuard, jose verifier (JWKS, issuer, exp, RS256, role), @Public
    ├── rate-limit/                global token bucket guard, @SkipRateLimit
    ├── fault-injection/           FAULT_RULES parser, injector, interceptor keyed by @FaultInjectionKey
    └── observability/             traceparent / X-Request-Id context (AsyncLocalStorage), pino, metrics
```

Request pipeline: correlation middleware → metrics middleware → pino-http → rate limit guard → JWT guard
→ fault injection interceptor → validation pipe → controller → use case → repository. Every error ends in
`ProblemDetailsFilter`, which renders `application/problem+json` with `traceId`; unexpected errors are
logged with their stack and returned as `500 INTERNAL_ERROR` without internal details.

## Swapping the in-memory adapter

The use case depends only on the `ClientRepository` port. A database adapter implements the same
interface and replaces the binding in `clients/infrastructure/client-repository.provider.ts`; nothing
else changes:

```ts
export class MongoClientRepository implements ClientRepository {
  constructor(private readonly collection: Collection<ClientDocument>) {}

  async findById(id: string): Promise<Client | null> {
    const document = await this.collection.findOne({ _id: id });
    return document === null ? null : toClient(document);
  }
}

export const clientRepositoryProvider: Provider<ClientRepository> = {
  provide: CLIENT_REPOSITORY,
  useFactory: (collection: Collection<ClientDocument>) => new MongoClientRepository(collection),
  inject: [CLIENTS_COLLECTION],
};
```

The e2e suite already exercises this seam by overriding `CLIENT_REPOSITORY` with a failing adapter.
