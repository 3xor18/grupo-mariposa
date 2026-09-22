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
  -e AUTH_AUDIENCE=clients-api \
  -e FAULT_INJECTION_ENABLED=true -e FAULT_RULES=CLI-40001:503:2,CLI-40002:503 clients-api
```

The image does not set `NODE_ENV`; production deployments set `NODE_ENV=production`, which makes the
service refuse to start with `FAULT_INJECTION_ENABLED=true`.

| Endpoint                                | Auth                          | Notes                                                              |
| --------------------------------------- | ----------------------------- | ------------------------------------------------------------------ |
| `GET /clients/{clientId}`               | Bearer, role `clients-reader` | `clientId` must match `^CLI-[A-Z0-9]{1,20}$`                       |
| `GET /health/live`, `GET /health/ready` | public                        | `{"status":"UP"}`; readiness is `503 DOWN` once shutdown starts    |
| `GET /metrics`                          | public (internal network)     | Prometheus: `http_requests_total`, `http_request_duration_seconds` |
| `GET /docs`, `GET /docs-json`           | public (internal network)     | Swagger UI / generated OpenAPI 3.1                                 |

`/metrics` and `/docs` are unauthenticated by design (ADR 0003): they are only reachable inside the
platform network and must not be published by an ingress.

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
Authentication fails closed: `AUTH_ENABLED=true` without issuer or JWKS URL stops the process.

| Variable                         | Default                  | Description                                                                |
| -------------------------------- | ------------------------ | -------------------------------------------------------------------------- |
| `PORT`                           | `3000`                   | HTTP port                                                                  |
| `LOG_LEVEL`                      | `info`                   | `fatal`, `error`, `warn`, `info`, `debug`, `trace`, `silent`               |
| `NODE_ENV`                       | unset                    | `production` forbids fault injection                                       |
| `AUTH_ENABLED`                   | `true`                   | `false` only for local tests                                               |
| `AUTH_ISSUER`                    | required when auth is on | expected `iss` claim                                                       |
| `AUTH_JWKS_URL`                  | required when auth is on | Keycloak JWKS endpoint (RS256 only)                                        |
| `AUTH_AUDIENCE`                  | unset (not checked)      | expected `aud` claim, e.g. `clients-api` (config key `auth.audience`)      |
| `AUTH_REQUIRED_ROLE`             | `clients-reader`         | role expected in `realm_access.roles`                                      |
| `FAULT_INJECTION_ENABLED`        | `false`                  | enables `FAULT_RULES`; refused when `NODE_ENV=production`                  |
| `FAULT_RULES`                    | empty                    | `id:type[:times]` list, types `429`, `500`, `502`, `503`, `400`, `timeout` |
| `FAULT_TIMEOUT_MS`               | `5000`                   | hold time of `timeout` rules (released early on cancel or shutdown)        |
| `RATE_LIMIT_RPS`                 | `200`                    | refill rate of every caller bucket                                         |
| `RATE_LIMIT_BURST`               | `400`                    | capacity of every caller bucket                                            |
| `RATE_LIMIT_MAX_TRACKED_CALLERS` | `10000`                  | LRU bound of buckets kept per layer                                        |
| `SHUTDOWN_DRAIN_MS`              | `5000`                   | time readiness reports `DOWN` before the server closes                     |
| `SHUTDOWN_TIMEOUT_MS`            | `10000`                  | extra time after the drain before a signalled shutdown is forced           |

### Rate limiting

Two token buckets protect every non-probe route: one per client address, checked before
authentication, and one per authenticated principal (`azp`, falling back to `sub`), checked after it.
Excess requests get `429 RATE_LIMITED` with `Retry-After` in seconds. Buckets live in bounded LRU maps,
so limits are **per instance**: with N replicas the effective limit is N times the configured one.

### Fault injection demo data

`CLI-40001` and `CLI-40002` are demo fixtures (`DEMO_RESILIENCE_CLIENTS` in the seed) used by the
platform resilience scenarios together with `FAULT_RULES=CLI-40001:503:2,CLI-40002:503`. With fault
injection disabled they behave as regular active clients.

### Graceful shutdown

On `SIGTERM`/`SIGINT`, readiness switches to `503 DOWN`, pending fault holds are released with
`503 SERVICE_UNAVAILABLE`, the service waits `SHUTDOWN_DRAIN_MS` and then closes the server,
destroying idle and remaining connections. If shutdown exceeds `SHUTDOWN_DRAIN_MS +
SHUTDOWN_TIMEOUT_MS` the process exits with code 1.

### Centralized configuration (Spring Cloud Config Server)

Before the Nest app is created, `loadRemoteConfig` fetches
`${CONFIG_SERVER_URL}/${CONFIG_APP_NAME}-${CONFIG_PROFILE}.properties` (application and profile are
URL-encoded). Keys are parsed with `java.util.Properties` rules (`key: value`, `key=value`, line
continuations, escapes such as `\:` `\=` `\\` `\uXXXX`, `#`/`!` comments) and mapped to environment
style (`rate-limit.rps` → `RATE_LIMIT_RPS`, `auth.jwks-url` → `AUTH_JWKS_URL`). Unknown keys (for
example `management.*`) are ignored.

Precedence: **defined environment variable > config server value > built-in default**. A variable that
is defined, even as an empty string, overrides the remote value; only undefined variables fall through.
The merged source is handed to the same validated loader and `process.env` is never mutated. Logs list
only the names of the loaded keys, never their values, and credentials are never logged.

| Variable                   | Default       | Description                                                                   |
| -------------------------- | ------------- | ----------------------------------------------------------------------------- |
| `CONFIG_SERVER_URL`        | empty (skip)  | config server base URL                                                        |
| `CONFIG_APP_NAME`          | `clients-api` | application name segment                                                      |
| `CONFIG_PROFILE`           | `default`     | profile segment (`docker` in Compose)                                         |
| `CONFIG_SERVER_USERNAME`   | none          | HTTP basic auth user                                                          |
| `CONFIG_SERVER_PASSWORD`   | none          | HTTP basic auth password                                                      |
| `CONFIG_SERVER_TIMEOUT_MS` | `3000`        | timeout per attempt (max 60000)                                               |
| `CONFIG_SERVER_RETRIES`    | `3`           | extra attempts on network errors, timeouts and `5xx` (max 10); `4xx` is final |
| `CONFIG_SERVER_FAIL_FAST`  | `false`       | `true`: fatal log and exit 1 when unreachable; `false`: warn and continue     |

Retries wait a random time between 50 % and 100 % of `min(5000, 200 × 2ⁿ)` ms.

## Module design

```
src/
├── clients/                       feature module
│   ├── domain/                    Client, ClientStatus, Segment, TaxRegime, Market, ClientNotFoundError
│   ├── application/               GetClientUseCase + ClientRepository port (CLIENT_REPOSITORY token)
│   ├── infrastructure/            InMemoryClientRepository, seed (master data + demo fixtures)
│   └── http/                      thin controller, GetClientParams (validation), ClientResponse, mapper
├── health/                        liveness/readiness, graceful shutdown drain
├── bootstrap/                     structured fatal logging for startup failures and unhandled errors
├── config/                        typed AppConfig loaded with zod (fail fast)
│   └── remote/                    Spring Cloud Config Server client, properties parser, merge
└── shared/
    ├── constants/                 HTTP headers, routes, environment and logging constants
    ├── errors/                    ErrorCode catalog, ProblemException, DomainError, global filter
    ├── auth/                      JwtAuthGuard, jose verifier (JWKS, issuer, audience, exp, RS256, role)
    ├── rate-limit/                per-address and per-principal token buckets, @SkipRateLimit
    ├── fault-injection/           FAULT_RULES parser, injector, interceptor, cancellable holds
    └── observability/             W3C traceparent / X-Request-Id context, pino, Prometheus metrics
```

Request pipeline: correlation middleware → metrics middleware → pino-http → address rate limit → JWT
guard → principal rate limit → fault injection interceptor → validation pipe → controller → use case →
repository. Every error ends in `ProblemDetailsFilter`, which renders `application/problem+json` with a
32-hex W3C `traceId`. Server errors (`5xx`) never expose exception messages: the detail comes from the
error catalog and the original error is logged with its stack.

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
