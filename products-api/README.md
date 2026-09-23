# products-api

Product catalog per market for the B2B orders platform. Go standard library HTTP server backed by
MongoDB, publishing `products.changed.v1` through a transactional outbox. Contract:
[`contracts/http/products-api.openapi.yaml`][openapi]. Errors follow
[ADR 0004](../docs/adr/0004-shared-error-contract.md); markets follow
[ADR 0006](../docs/adr/0006-configurable-market-catalog.md); change events follow
[ADR 0007](../docs/adr/0007-master-data-change-events-and-cache.md).

[openapi]: ../contracts/http/products-api.openapi.yaml

## Run

```bash
AUTH_ENABLED=false STORAGE_DRIVER=memory SEED_ENABLED=true go run ./cmd/products-api

docker build -t products-api .
docker run --rm -p 8081:8081 -e AUTH_ENABLED=false -e STORAGE_DRIVER=memory \
  -e SEED_ENABLED=true products-api
curl -i "http://localhost:8081/products/PRD-001?market=CL"
curl -i -X PATCH -H 'If-Match: "1"' -d '{"status":"DISCONTINUED"}' \
  "http://localhost:8081/products/PRD-020?market=MX"
```

The binary doubles as its own container probe: `products-api -healthcheck` reads only `PORT`
(and `HEALTHCHECK_TIMEOUT_MS`) from the environment and performs `GET /health/live`, exiting
`0`/`1`. It never validates the rest of the configuration nor calls the config server, so the
distroless image (no shell, no curl) stays probe-able even when auth settings come from the
config server. Both base images are pinned by exact tag and digest (`ARG`s in the Dockerfile).

## Test and lint

```bash
go test ./... -coverprofile=coverage.out
go tool cover -func=coverage.out
golangci-lint run ./...
```

`-race` needs cgo (a C toolchain). Without one locally, run the suite in a container from the
repository root:

```bash
docker run --rm -v "$PWD:/work" -w /work/products-api golang:1.26 go test ./... -race
```

The contract tests load `../contracts` (the repository-level contracts are the source of truth)
and validate real handler responses (GET and PATCH, every documented problem status and health)
against the OpenAPI document and `contracts/common/problem.schema.json`, and every change event
against `contracts/events/products.changed.v1.schema.json`.

Integration tests use Testcontainers (`mongo:7.0.26` as a single-node replica set and
`apache/kafka:3.9.1` in KRaft mode) and need a Docker daemon: the MongoDB adapter, the Kafka
producer and an end-to-end test (`PATCH` → Mongo transaction → outbox → relay → Kafka record
validated against the schema). Set `SKIP_INTEGRATION=1` to skip them. For `-race` inside a
container, mount the Docker socket and point Testcontainers at the host:

```bash
docker run --rm -v "$PWD:/work" -v /var/run/docker.sock:/var/run/docker.sock \
  -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal -w /work/products-api \
  golang:1.26 go test ./... -race
```

## Endpoints

All routes answer `GET` and `HEAD`; the product route also answers `PATCH`.

| Method | Path | Role | Notes |
|---|---|---|---|
| GET | `/products/{productId}?market=` | `products-reader` | product with `version` and `ETag` |
| PATCH | `/products/{productId}?market=` | `products-admin` | partial update, see below |
| GET | `/health/live` | none | always `{"status":"UP"}` |
| GET | `/health/ready` | none | `503 DOWN`: no JWKS keys yet, MongoDB down or draining |
| GET | `/metrics` | none | Prometheus, see below |

`market` must belong to the catalog (`PLATFORM_MARKETS`), otherwise `400 VALIDATION_ERROR`.
`PATCH` takes a JSON object with at least one of `status`, `taxCategory`, `name` (unknown or
non-string fields are rejected). The response carries the current `version` and `ETag`. A patch
whose values already match the product is a no-op: `200` with the current version, no version
bump and no change event. Faults from `FAULT_RULES` only apply to reads.

`If-Match` follows RFC 9110 strong comparison:

- absent or `*`: unconditional;
- otherwise a comma-separated list of entity tags; a strong tag `"N"` (or bare `N`) matches when
  `N` is the current version; weak tags `W/"N"` never match;
- a valid list without a match (including `"abc"`) returns `412 PRECONDITION_FAILED`;
- a malformed header (empty item, unbalanced quote, `W/` without quotes) returns `400`.

Any other method on these paths returns `405 METHOD_NOT_ALLOWED` with an `Allow` header; unknown
paths return `404 RESOURCE_NOT_FOUND` with a static detail (request input is never reflected).
`/metrics` is unauthenticated on purpose: it is meant to be scraped from the internal network
only and must not be published through an ingress or gateway.

Error codes: `VALIDATION_ERROR` (400, with `errors[]`), `UNAUTHORIZED` (401, invalid or missing
token), `FORBIDDEN` (403, missing role), `PRODUCT_NOT_FOUND` (404, unknown product or not sold in
that market), `RESOURCE_NOT_FOUND` (404), `METHOD_NOT_ALLOWED` (405), `PRECONDITION_FAILED`
(412), `RATE_LIMITED` (429 +
`Retry-After`), `CLIENT_CLOSED_REQUEST` (499, the caller cancelled; nobody reads it but it keeps
metrics honest), `INTERNAL_ERROR` (500), `BAD_GATEWAY` (502, fault injection only) and
`SERVICE_UNAVAILABLE` (503: request deadline exceeded, JWKS unreachable, or injected fault).
Every problem carries `Cache-Control: no-store`; every response carries
`X-Content-Type-Options: nosniff`.

Metrics: `http_server_requests_total` and `http_server_request_duration_seconds`, labelled by
`method` (normalized to `GET`, `HEAD`, `POST`, `PUT`, `PATCH`, `DELETE`, `OPTIONS` or `OTHER` to
keep cardinality bounded), `route` (the matched pattern) and `status`; plus `outbox_pending`,
`outbox_oldest_age_seconds`, `outbox_published_total` and `outbox_publish_failures_total` from
the relay.

## Configuration

Configuration is parsed once into a typed struct and validated as a whole; the process refuses
to start listing every invalid variable. Durations are in milliseconds.

### Server

| Variable | Default | Description |
|---|---|---|
| `PORT` | `8081` | listen port, environment only (see below) |
| `APP_ENV` | empty | `production` forbids `FAULT_INJECTION_ENABLED=true` |
| `REQUEST_TIMEOUT_MS` | `3000` | per-request deadline, propagated to the repository |
| `SHUTDOWN_DRAIN_DELAY_MS` | `3000` | keep serving with readiness `DOWN` after SIGTERM |
| `SHUTDOWN_TIMEOUT_MS` | `10000` | then wait for in-flight requests, then close |
| `HTTP_READ_HEADER_TIMEOUT_MS` | `5000` | `http.Server.ReadHeaderTimeout` |
| `HTTP_READ_TIMEOUT_MS` | `10000` | `http.Server.ReadTimeout` |
| `HTTP_WRITE_TIMEOUT_MS` | `15000` | `http.Server.WriteTimeout`, above `REQUEST_TIMEOUT_MS` |
| `HTTP_IDLE_TIMEOUT_MS` | `60000` | `http.Server.IdleTimeout` |
| `HTTP_MAX_HEADER_BYTES` | `16384` | `http.Server.MaxHeaderBytes` (max 1 MiB) |
| `PROBLEM_TYPE_BASE_URL` | see below | prefix of the problem `type` URI |
| `LOG_LEVEL` | `INFO` | `DEBUG`, `INFO`, `WARN`, `ERROR` |
| `HEALTHCHECK_TIMEOUT_MS` | `2000` | timeout of the `-healthcheck` probe |

`PORT` is a deployment concern: it is read only from the environment and ignored when it comes
from the config server. The image sets `ENV PORT=8081`. The default problem type base is
`https://contracts.grupomariposa.dev/problems/`.

With the defaults a stop needs up to `SHUTDOWN_DRAIN_DELAY_MS + SHUTDOWN_TIMEOUT_MS` (13 s), so
set Compose `stop_grace_period` (or Kubernetes `terminationGracePeriodSeconds`) above that.

### Rate limiting and fault injection

| Variable | Default | Description |
|---|---|---|
| `RATE_LIMIT_RPS` | `200` | token bucket refill rate, per caller |
| `RATE_LIMIT_BURST` | `400` | token bucket size, per caller |
| `RATE_LIMIT_MAX_KEYS` | `10000` | callers tracked per limiter (LRU eviction) |
| `FAULT_INJECTION_ENABLED` | `false` | master switch; `FAULT_RULES` are ignored unless `true` |
| `FAULT_RULES` | empty | `id:type[:times]`, types `400,429,500,502,503,timeout` |
| `FAULT_TIMEOUT_MS` | `5000` | hold of a `timeout` fault, bounded by the request deadline |

Fault injection is refused at startup when `APP_ENV=production` (case-insensitive).

### Authentication

| Variable | Default | Description |
|---|---|---|
| `AUTH_ENABLED` | `true` | disable only for local tests |
| `AUTH_ISSUER` | required | expected `iss` claim |
| `AUTH_AUDIENCE` | `products-api` | expected `aud` claim; always checked, blank is rejected |
| `AUTH_JWKS_URL` | required | Keycloak JWKS endpoint |
| `AUTH_REQUIRED_ROLE` | `products-reader` | realm role required to read |
| `AUTH_ADMIN_ROLE` | `products-admin` | realm role required to `PATCH` |
| `AUTH_CLOCK_LEEWAY_MS` | `30000` | tolerance for `exp`/`nbf` clock skew |
| `AUTH_JWKS_TIMEOUT_MS` | `2000` | timeout of each JWKS download |
| `AUTH_JWKS_REFRESH_INTERVAL_MS` | `3600000` | periodic JWKS refresh |
| `AUTH_JWKS_MIN_REFRESH_INTERVAL_MS` | `10000` | gap between on-demand refreshes and retries |

### Markets, persistence and change events

| Variable | Default | Description |
|---|---|---|
| `PLATFORM_MARKETS` | the five markets | `CODE:CURRENCY:LOCALE,...`, validated at startup |
| `PLATFORM_CURRENCIES` | `MXN:2,COP:2,PEN:2,CLP:0,USD:2` | `CODE:DIGITS` (0-4) |
| `SEED_ENABLED` | `false` | insert missing seed rows at startup; refused in production |
| `STORAGE_DRIVER` | `mongo` | `memory` only for local runs and tests; refused in production |
| `MONGODB_URI` | required (secret) | connection string, replica set needed for transactions |
| `MONGODB_DATABASE` | `products` | database with `products` and `outbox` |
| `MONGODB_TIMEOUT_MS` | `5000` | operation and server selection timeout, also readiness ping |
| `KAFKA_BOOTSTRAP_SERVERS` | required with `mongo` | comma-separated brokers |
| `KAFKA_TOPIC_CHANGES` | `products.changed.v1` | topic of the change events |
| `KAFKA_TLS_ENABLED` | `false` | TLS 1.2+ to the brokers; must be `true` in production |
| `OUTBOX_RELAY_INTERVAL_MS` | `250` | relay polling interval |
| `OUTBOX_BATCH_SIZE` | `100` | events claimed per cycle (max 1000) |
| `OUTBOX_LEASE_MS` | `30000` | lease of a claimed event; publishing is bounded by half of it |
| `OUTBOX_RETRY_DELAY_MS` | `1000` | wait before retrying an event whose publication failed |
| `OUTBOX_RETENTION` | `7d` | TTL of published events (`d`, `h`, `m`, `s` units) |

The default catalog is `MX:MXN:es-MX,CO:COP:es-CO,PE:PEN:es-PE,CL:CLP:es-CL,EC:USD:es-EC`
(config keys `platform.markets` and `platform.currencies`, shared by the four services). Entries
are trimmed; codes must match `^[A-Z]{2}$`, currencies `^[A-Z]{3}$`, locales `^[a-z]{2}-[A-Z]{2}$`;
duplicates and markets whose currency is not declared stop the startup.

With `APP_ENV=production` the service refuses `STORAGE_DRIVER=memory`, `SEED_ENABLED=true`,
`FAULT_INJECTION_ENABLED=true` and `KAFKA_TLS_ENABLED=false`.

### Centralized configuration (Spring Cloud Config Server)

When `CONFIG_SERVER_URL` is set, startup fetches
`${CONFIG_SERVER_URL}/${CONFIG_APP_NAME}-${CONFIG_PROFILE}.properties` (both segments are
path-escaped) and maps each property to its env-style name (uppercase, `.` and `-` become `_`):
`rate-limit.rps` → `RATE_LIMIT_RPS`, `fault.injection-enabled` → `FAULT_INJECTION_ENABLED`.
Parsing follows `java.util.Properties`: the first unescaped `=`, `:` or blank separates key and
value (so `auth.jwks-url: http://host:8080/...` keeps the URL intact), `#`/`!` comments,
`\:` `\=` `\\` `\t` `\n` `\uXXXX` escapes (UTF-16 surrogate pairs are combined), line
continuations; blanks are space, tab and form feed. Keys the service does not use (for example
`management.*`) are ignored. The response must be `text/plain` and at most 1 MiB.

Precedence: **environment variable > config server > built-in default**. An environment variable
that is *defined* wins even when it is empty (an empty value then means "use the default"); only
unset variables fall through to the config server. Remote values are layered behind the
environment through the lookup function passed to `config.Load`; the process environment is
never mutated. Only the property **keys** and their count are logged, never values, and the
server password is never logged.

| Variable | Default | Description |
|---|---|---|
| `CONFIG_SERVER_URL` | empty (disabled) | base URL of the config server |
| `CONFIG_APP_NAME` | `products-api` | application name in the properties path |
| `CONFIG_PROFILE` | `default` | profile in the properties path (Compose uses `docker`) |
| `CONFIG_SERVER_USERNAME` | empty | HTTP basic auth user |
| `CONFIG_SERVER_PASSWORD` | empty | HTTP basic auth password |
| `CONFIG_SERVER_TIMEOUT_MS` | `3000` | timeout per attempt |
| `CONFIG_SERVER_RETRIES` | `3` | extra attempts (0-10) on network errors, `429` and `5xx` |
| `CONFIG_SERVER_BACKOFF_MS` | `200` | first retry delay, doubled on each retry, with jitter |
| `CONFIG_SERVER_BACKOFF_MAX_MS` | `2000` | cap for the retry delay |
| `CONFIG_SERVER_FAIL_FAST` | `false` | abort startup if the server stays unreachable |

Without fail-fast, an unreachable config server produces a JSON warning and the service starts
with environment values and defaults.

## Design

```
cmd/products-api        signal handling and exit code only
internal/product        domain: Product, Market, Status, TaxCategory, Repository port, errors
internal/catalog        application service: input validation and lookup use case
internal/market         market catalog value objects (PLATFORM_MARKETS)
internal/seed           seed rows from the platform conventions
internal/storage/memory in-memory adapter for tests and local runs
internal/storage/mongodb MongoDB adapter: products, transactional outbox, lease claims
internal/outbox         relay: claim with lease, publish, confirm or release
internal/kafka          idempotent franz-go producer (acks=all)
internal/httpapi        transport: routing, handlers, DTOs, problem details, middleware
internal/auth           JWT verification (RS256, iss, aud, exp, role) against a JWKS cache
internal/ratelimit      bounded LRU of per-key token buckets
internal/fault          FAULT_RULES parsing and thread-safe per-id counters
internal/telemetry      JSON slog logger with traceId, Prometheus registry
internal/config         typed env reader, parsing and validation
internal/config/remote  Spring Cloud Config Server client, properties parser, layered lookup
internal/app            composition root, server lifecycle, drain and shutdown, healthcheck mode
```

- **Standard library router** ([ADR 0005](../docs/adr/0005-go-standard-library-router.md)).
  Go 1.22 `ServeMux` patterns (`GET /products/{productId}`) cover method matching and path
  parameters. Middleware is plain `func(http.Handler) http.Handler`, so there is no framework to
  learn, upgrade or patch, and every piece is tested with `httptest`. External modules are
  limited to what the standard library lacks: JWT (`golang-jwt`), JWK parsing (`jwkset`),
  Prometheus, `x/time/rate`, and kin-openapi in tests.
- **Middleware order.** Global: trace context, security headers, access log and metrics, panic
  recovery. Product route: per-client-address rate limit, request deadline, authentication,
  per-principal rate limit, fault injection, handler. The deadline wraps authentication and
  faults, so a `timeout` fault longer than `REQUEST_TIMEOUT_MS` ends in `503`, like a real slow
  dependency would.
- **Rate limiting.** Two token buckets per caller: one keyed by the remote IP before
  authentication (protects the JWT verification) and one keyed by the token principal (`azp`,
  falling back to `sub`) after it, so callers sharing an address behind a NAT or gateway are not
  throttled together. `Retry-After` is derived from the bucket's reservation delay. Limits are
  **per instance**: with N replicas the effective limit per caller is N times higher. The remote
  IP is the TCP peer; `X-Forwarded-For` is intentionally not trusted.
- **Authentication.** Signing keys come from a JWKS cache that is warmed in the background and
  refreshed periodically and on unknown `kid` (rate limited). Readiness stays `DOWN` until a
  download yields at least one usable key; an empty key set never counts as loaded. The
  audience is always enforced. Token problems (signature, `iss`, `aud`, `exp`, `nbf`, `alg`,
  unknown `kid`) are `401`, a missing role is `403`, and an unreachable JWKS or cancelled
  context is `503`, so callers retry instead of giving up.
- **Cancellation.** The request context (with its deadline) flows through the service into
  `Repository.FindByIDInMarket`, which checks `ctx.Err()`. A deadline maps to `503`, a client
  cancellation to `499`, never to another 4xx.
- **Shutdown.** On SIGTERM readiness turns `DOWN`, the server keeps serving for
  `SHUTDOWN_DRAIN_DELAY_MS`, then `Shutdown` waits up to `SHUTDOWN_TIMEOUT_MS` for in-flight
  requests. Background work (JWKS refresh) is stopped only after the server has stopped.
- **Tracing.** A valid W3C `traceparent` is honoured; otherwise a trace id is generated. The id
  is returned in `traceparent`, `X-Request-Id`, every log line and every problem body.
- **Persistence and change events.** Reads go through the `product.Repository` port and writes
  through `product.Writer`; the MongoDB adapter implements both and the memory adapter keeps
  serving unit tests. A `PATCH` runs in one MongoDB transaction that re-reads the product, checks
  `If-Match`, increments `version` and inserts the `products.changed.v1` event (UUIDv7 id, key
  `market:productId`) into `outbox`; a no-op patch writes nothing. With `SEED_ENABLED=true` seed
  rows are upserted with `$setOnInsert`, so restarts never overwrite changes made through the
  API. `{productId, market}` is a unique index; published outbox events expire through a TTL
  index on `publishedAt` (`OUTBOX_RETENTION`, updated in place when the value changes).
- **Outbox relay.** A background goroutine claims events with a lease (`IN_FLIGHT`, owner,
  `leaseUntil`), only the oldest unpublished version per key so a key is never published out of
  order, publishes them with an idempotent producer (`acks=all`), and marks them `PUBLISHED`
  only if it still owns the lease (fencing). Failures go back to `PENDING` with `attempts` and a
  retry delay; expired leases are reclaimed by any instance. Delivery is at least once, so
  consumers keep the highest `version` per key. Readiness pings MongoDB but not Kafka: the relay
  retries on its own. Shutdown stops the relay after the HTTP server, then closes the Kafka
  client and MongoDB.
