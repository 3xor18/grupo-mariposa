# clients-api

Distributor master data service (NestJS 11, TypeScript strict). Contracts:
[`contracts/http/clients-api.openapi.yaml`](../contracts/http/clients-api.openapi.yaml),
[`contracts/events/clients.changed.v1.schema.json`](../contracts/events/clients.changed.v1.schema.json);
errors follow [`contracts/common/problem.schema.json`](../contracts/common/problem.schema.json)
(ADR 0004). Market catalog and change events follow ADR 0006 and ADR 0007.

## Run

MongoDB must run as a replica set (transactions). Kafka is optional locally: without
`KAFKA_BOOTSTRAP_SERVERS` changes stay in the outbox until a relay can publish them.

```bash
npm ci
npm run build
AUTH_ENABLED=false MONGODB_URI='mongodb://localhost:27017/?replicaSet=rs0' npm start
```

Docker (host port 8082 per `docs/platform-conventions.md`):

```bash
docker build -t clients-api .
docker run --rm -p 8082:3000 \
  -e MONGODB_URI='mongodb://clients:secret@mongo:27017/clients?replicaSet=rs0' \
  -e KAFKA_BOOTSTRAP_SERVERS=kafka:9092 \
  -e AUTH_ISSUER=http://localhost:8180/realms/mariposa \
  -e AUTH_JWKS_URL=http://keycloak:8080/realms/mariposa/protocol/openid-connect/certs \
  -e API_DOCS_ENABLED=true -e TRUST_PROXY=1 \
  -e FAULT_INJECTION_ENABLED=true -e FAULT_RULES=CLI-40001:503:2,CLI-40002:503 clients-api
```

The image is pinned to `node:24.21.0-alpine3.24` and does not set `NODE_ENV`. Production deployments
set `NODE_ENV=production`, which makes the service refuse to start with `AUTH_ENABLED=false`,
`FAULT_INJECTION_ENABLED=true`, `API_DOCS_ENABLED=true`, `STORAGE_DRIVER=memory` or without
`KAFKA_BOOTSTRAP_SERVERS`.

| Endpoint                                | Auth                          | Notes                                                                  |
| --------------------------------------- | ----------------------------- | ---------------------------------------------------------------------- |
| `GET /clients/{clientId}`               | Bearer, role `clients-reader` | `clientId` must match `^CLI-[A-Z0-9]{1,20}$`; responds `ETag: "<v>"`   |
| `PATCH /clients/{clientId}`             | Bearer, role `clients-admin`  | `status`, `segment`, `taxRegime`; stale `If-Match` returns `412`       |
| `GET /health/live`, `GET /health/ready` | public                        | `{"status":"UP"}`; ready is `503 DOWN` when Mongo fails or on shutdown |
| `GET /metrics`                          | public (internal network)     | HTTP metrics plus `outbox_published_total`, failures and backlog gauge |
| `GET /docs`, `GET /docs-json`           | public, only if enabled       | Swagger UI / generated OpenAPI 3.1 (`API_DOCS_ENABLED`)                |

`/metrics` and `/docs` are unauthenticated by design (ADR 0003): they are only reachable inside the
platform network and must not be published by an ingress. `/docs` is mounted only when
`API_DOCS_ENABLED=true` (the docker profile of the config repository enables it).

### Updating a client

```bash
curl -i http://localhost:8082/clients/CLI-70001 -H "Authorization: Bearer $READER"   # ETag: "1"
curl -i -X PATCH http://localhost:8082/clients/CLI-70001 \
  -H "Authorization: Bearer $ADMIN" -H 'If-Match: "1"' -H 'Content-Type: application/json' \
  -d '{"status":"BLOCKED"}'                                                          # ETag: "2"
```

The body accepts only `status`, `segment` and `taxRegime` (at least one; unknown fields are `400`).
`If-Match` accepts `"<version>"`, `<version>` or `*`; without it the update is unconditional. A stale
version returns `412 PRECONDITION_FAILED`.

## Test

```bash
npm run lint          # ESLint (typescript-eslint strict + sonarjs) and Prettier
npm run typecheck
npm test              # unit (src/**/*.spec.ts) + e2e/contract/integration (test/*.e2e-spec.ts)
npm run test:cov      # fails below 100% lines/branches/functions/statements (main.ts excluded)
```

Tests need Docker: a Jest global setup starts `mongo:7.0.26` as a replica set with Testcontainers and
every test app uses its own database. The relay integration test starts `apache/kafka:3.9.1`, patches a
client through HTTP and consumes `clients.changed.v1`. Ryuk uses the local
`testcontainers/ryuk:0.12.0` image (override with `RYUK_CONTAINER_IMAGE`). Contract tests load
`../contracts` and validate HTTP responses and event payloads with Ajv, and compare the generated
OpenAPI document (operations, status codes, `Client` schema) against the contract.

## Environment

The configuration is validated at startup; the process exits with a `fatal` log on any invalid value.
Authentication fails closed: `AUTH_ENABLED=true` without issuer or JWKS URL stops the process.

| Variable                         | Default                  | Description                                                                 |
| -------------------------------- | ------------------------ | --------------------------------------------------------------------------- |
| `PORT`                           | `3000`                   | HTTP port                                                                   |
| `LOG_LEVEL`                      | `info`                   | `fatal`, `error`, `warn`, `info`, `debug`, `trace`, `silent`                |
| `NODE_ENV`                       | unset                    | `production` forbids auth off, fault injection, API docs and no Kafka       |
| `PLATFORM_MARKETS`               | 5 markets (see below)    | `CODE:CURRENCY:locale,...` catalog (config key `platform.markets`)          |
| `STORAGE_DRIVER`                 | `mongo`                  | `mongo` or `memory` (seeded, no events; refused when `NODE_ENV=production`) |
| `MONGODB_URI`                    | required with `mongo`    | secret `mongodb://` or `mongodb+srv://` connection string of a replica set  |
| `MONGODB_DATABASE`               | `clients`                | database with the `clients` and `outbox` collections                        |
| `KAFKA_BOOTSTRAP_SERVERS`        | empty (relay disabled)   | comma separated brokers; required when `NODE_ENV=production`                |
| `KAFKA_TOPIC_CHANGES`            | `clients.changed.v1`     | topic of the change events (key `clientId`)                                 |
| `OUTBOX_RELAY_INTERVAL_MS`       | `250`                    | pause between relay iterations                                              |
| `OUTBOX_BATCH_SIZE`              | `100`                    | outbox entries leased per iteration                                         |
| `OUTBOX_LEASE_MS`                | `30000`                  | lease length before another relay may take over an entry                    |
| `AUTH_ENABLED`                   | `true`                   | `false` only for local tests                                                |
| `AUTH_ISSUER`                    | required when auth is on | expected `iss` claim                                                        |
| `AUTH_JWKS_URL`                  | required when auth is on | Keycloak JWKS endpoint (RS256 only)                                         |
| `AUTH_AUDIENCE`                  | `clients-api`            | required `aud` claim, never blank (config key `auth.audience`)              |
| `AUTH_REQUIRED_ROLE`             | `clients-reader`         | realm role for reads                                                        |
| `AUTH_ADMIN_ROLE`                | `clients-admin`          | realm role for `PATCH /clients/{clientId}`                                  |
| `FAULT_INJECTION_ENABLED`        | `false`                  | enables `FAULT_RULES`; refused when `NODE_ENV=production`                   |
| `FAULT_RULES`                    | empty                    | `id:type[:times]` list, types `429`, `500`, `502`, `503`, `400`, `timeout`  |
| `FAULT_TIMEOUT_MS`               | `5000`                   | hold time of `timeout` rules (released early on cancel or shutdown)         |
| `RATE_LIMIT_RPS`                 | `200`                    | refill rate of every caller bucket                                          |
| `RATE_LIMIT_BURST`               | `400`                    | capacity of every caller bucket                                             |
| `RATE_LIMIT_MAX_TRACKED_CALLERS` | `10000`                  | LRU bound of buckets kept per layer                                         |
| `SHUTDOWN_DRAIN_MS`              | `5000`                   | time readiness reports `DOWN` before the server closes                      |
| `SHUTDOWN_TIMEOUT_MS`            | `10000`                  | extra time after the drain before a signalled shutdown is forced            |
| `API_DOCS_ENABLED`               | `false`                  | mounts Swagger at `/docs`; refused when `NODE_ENV=production`               |
| `TRUST_PROXY`                    | `false`                  | Express `trust proxy`: `true`, hop count or address list (behind ingress)   |

### Market catalog (ADR 0006)

Markets are not an enum: `PLATFORM_MARKETS` (default
`MX:MXN:es-MX,CO:COP:es-CO,PE:PEN:es-PE,CL:CLP:es-CL,EC:USD:es-EC`) is validated at startup (two
letter code, ISO 4217 currency, `xx-XX` locale, no duplicates; currencies may repeat). Only seed
clients whose market belongs to the catalog are loaded, and the API publishes `market` with the
contract pattern `^[A-Z]{2}$`. `PLATFORM_CURRENCIES` belongs to `order-processor` and is ignored here.

### Persistence and change events (ADR 0007)

- `clients` collection: one document per client with a unique `clientId` index, `version` (starts at
  1. and `updatedAt`. The seed (master data, CL/EC clients, `CLI-40001`/`CLI-40002` resilience fixtures
     and the `CLI-70001` cache demo client) is applied on every start with upserts using `$setOnInsert`,
     so changes made through the API survive restarts.
- `PATCH` runs a MongoDB transaction that updates the client (`version + 1`) and inserts a
  `clients.changed.v1` event (full state, `eventId` UUIDv7) into the `outbox` collection.
- `OutboxRelay` leases up to `OUTBOX_BATCH_SIZE` entries (`IN_FLIGHT` + owner + `leaseUntil`),
  publishes them with an idempotent kafkajs producer (key `clientId`, headers `eventId` and
  `contentType`) and marks them `PUBLISHED` only while it still owns the lease. Publish failures
  release the entries for retry and expired leases are taken over by other instances, so delivery is
  at least once and consumers keep the highest `version` per key. Shutdown stops the timer, waits for
  the running batch and disconnects the producer.

### Rate limiting

Two token buckets protect every non-probe route: one per client address, checked before
authentication, and one per authenticated principal (`azp`, falling back to `sub`), checked after it.
Excess requests get `429 RATE_LIMITED` with `Retry-After` in seconds. Behind an ingress set
`TRUST_PROXY` (for example `1`) so the address comes from `X-Forwarded-For`. Buckets live in bounded
LRU maps, so limits are **per instance**: with N replicas the effective limit is N times the
configured one.

### Demo fixtures

`CLI-40001` and `CLI-40002` (`DEMO_RESILIENCE_CLIENTS`) are used by the platform resilience scenarios
together with `FAULT_RULES=CLI-40001:503:2,CLI-40002:503`; with fault injection disabled they behave as
regular active clients. `CLI-70001` (`DEMO_CACHE_CLIENTS`) is blocked and reactivated by the cache
invalidation scenarios.

### Graceful shutdown

On `SIGTERM`/`SIGINT`, readiness switches to `503 DOWN`, pending fault holds are released with
`503 SERVICE_UNAVAILABLE`, the outbox relay finishes its batch, the service waits `SHUTDOWN_DRAIN_MS`,
closes the server (destroying remaining connections) and finally the MongoDB client. If shutdown
exceeds `SHUTDOWN_DRAIN_MS + SHUTDOWN_TIMEOUT_MS` the process exits with code 1.

### Centralized configuration (Spring Cloud Config Server)

Before the Nest app is created, `loadRemoteConfig` fetches
`${CONFIG_SERVER_URL}/${CONFIG_APP_NAME}-${CONFIG_PROFILE}.properties` (application and profile are
URL-encoded). Keys are parsed with `java.util.Properties` rules (`key: value`, `key=value`, line
continuations, escapes such as `\:` `\=` `\\` `\uXXXX`, `#`/`!` comments) and mapped to environment
style (`rate-limit.rps` → `RATE_LIMIT_RPS`, `platform.markets` → `PLATFORM_MARKETS`). Unknown keys
(for example `management.*`) are ignored.

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
│   ├── domain/                    Client (version), ClientChanges, not found / version conflict errors
│   ├── application/               Get/Update use cases, ClientRepository port, ClientChangedEvent
│   ├── infrastructure/            seed, InMemoryClientRepository (unit tests), provider binding
│   │   └── mongo/                 MongoClientRepository (transaction + outbox), seeding initializer
│   └── http/                      thin controller, DTOs, If-Match / ETag handling, OpenAPI decorators
├── health/                        liveness/readiness (Mongo ping), graceful shutdown drain
├── bootstrap/                     structured fatal logging for startup failures and unhandled errors
├── config/                        typed AppConfig loaded with zod (fail fast)
│   └── remote/                    Spring Cloud Config Server client, properties parser, merge
└── shared/
    ├── markets/                   market catalog parsed from PLATFORM_MARKETS
    ├── mongo/                     MongoClient lifecycle and database health
    ├── outbox/                    outbox documents, leasing store, Kafka publisher, relay, metrics
    ├── ids/, time/                UUIDv7 generator and clock (injectable)
    ├── constants/                 HTTP headers, routes, environment and logging constants
    ├── errors/                    ErrorCode catalog, ProblemException, DomainError, global filter
    ├── auth/                      JwtAuthGuard, reader/admin roles, jose verifier (JWKS, audience)
    ├── rate-limit/                per-address and per-principal token buckets, @SkipRateLimit
    ├── fault-injection/           FAULT_RULES parser, injector, interceptor, cancellable holds
    └── observability/             W3C trace context, pino, shared Prometheus registry
```

Request pipeline: correlation middleware → metrics middleware → pino-http → address rate limit → JWT
guard (reader or admin role) → principal rate limit → fault injection interceptor → validation pipe →
controller → use case → repository. Every error ends in `ProblemDetailsFilter`, which renders
`application/problem+json` with a 32-hex W3C `traceId`. Server errors (`5xx`) never expose exception
messages: the detail comes from the error catalog and the original error is logged with its stack.

## Swapping the repository adapter

The use cases depend only on the `ClientRepository` port (`findById`, `update`), bound to the
`CLIENT_REPOSITORY` token in `clients/infrastructure/client-repository.provider.ts`. Production binds
`MongoClientRepository`; `InMemoryClientRepository` implements the same contract (versioning,
conflicts, recorded change events) for unit tests and `STORAGE_DRIVER=memory` (same parity switch as
`products-api`; the relay is not started and events are only kept in memory). Another store only needs an adapter that persists
the change and its `ClientChangedEvent` atomically and a different `useFactory`, for example:

```ts
export const clientRepositoryProvider: Provider<ClientRepository> = {
  provide: CLIENT_REPOSITORY,
  useFactory: (catalog: MarketCatalog) => new InMemoryClientRepository(seedForCatalog(catalog)),
  inject: [MARKET_CATALOG],
};
```

The e2e suite exercises this seam by overriding `CLIENT_REPOSITORY` with a failing adapter.
