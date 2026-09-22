# products-api

Product catalog per market (MX, CO, PE) for the B2B orders platform. Go standard library HTTP
server, contract in [`contracts/http/products-api.openapi.yaml`](../contracts/http/products-api.openapi.yaml),
errors per [ADR 0004](../docs/adr/0004-shared-error-contract.md).

## Run

```bash
AUTH_ENABLED=false go run ./cmd/products-api

docker build -t products-api .
docker run --rm -p 8081:8081 -e AUTH_ENABLED=false products-api
curl "http://localhost:8081/products/PRD-001?market=MX"
```

The binary doubles as its own container probe: `products-api -healthcheck` reads only `PORT`
(and `HEALTHCHECK_TIMEOUT_MS`) from the environment and performs `GET /health/live`, exiting
`0`/`1`. It never validates the rest of the configuration nor calls the config server, so the
distroless image (no shell, no curl) stays probe-able even when auth settings come from the
config server.

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
and validate real handler responses (200, every documented problem status and health) against
the OpenAPI document and `contracts/common/problem.schema.json`.

## Endpoints

| Method | Path | Auth | Notes |
|---|---|---|---|
| GET, HEAD | `/products/{productId}?market=MX\|CO\|PE` | Bearer JWT with realm role | 200 product, problem otherwise |
| GET, HEAD | `/health/live` | none | always `{"status":"UP"}` |
| GET, HEAD | `/health/ready` | none | `503 {"status":"DOWN"}` until the JWKS is loaded and while draining |
| GET, HEAD | `/metrics` | none | Prometheus, see below |

Any other method on these paths returns `405 METHOD_NOT_ALLOWED` with `Allow: GET, HEAD`; unknown
paths return `404 RESOURCE_NOT_FOUND` with a static detail (request input is never reflected).
`/metrics` is unauthenticated on purpose: it is meant to be scraped from the internal network
only and must not be published through an ingress or gateway.

Error codes: `VALIDATION_ERROR` (400, with `errors[]`), `UNAUTHORIZED` (401, invalid or missing
token), `FORBIDDEN` (403, missing role), `PRODUCT_NOT_FOUND` (404, unknown product or not sold in
that market), `RESOURCE_NOT_FOUND` (404), `METHOD_NOT_ALLOWED` (405), `RATE_LIMITED` (429 +
`Retry-After`), `CLIENT_CLOSED_REQUEST` (499, the caller cancelled; nobody reads it but it keeps
metrics honest), `INTERNAL_ERROR` (500), `BAD_GATEWAY` (502, fault injection only) and
`SERVICE_UNAVAILABLE` (503: request deadline exceeded, JWKS unreachable, or injected fault).
Every problem carries `Cache-Control: no-store`; every response carries
`X-Content-Type-Options: nosniff`.

Metrics: `http_server_requests_total` and `http_server_request_duration_seconds`, labelled by
`method` (normalized to `GET`, `HEAD`, `POST`, `PUT`, `PATCH`, `DELETE`, `OPTIONS` or `OTHER` to
keep cardinality bounded), `route` (the matched pattern) and `status`.

## Configuration

Configuration is parsed once into a typed struct and validated as a whole; the process refuses
to start listing every invalid variable.

| Variable | Default | Description |
|---|---|---|
| `PORT` | `8081` | listen port; **environment only** (a deployment concern, ignored if it comes from the config server; the image sets `ENV PORT=8081`) |
| `REQUEST_TIMEOUT_MS` | `3000` | per-request deadline propagated down to the repository |
| `SHUTDOWN_DRAIN_DELAY_MS` | `3000` | after SIGTERM keep serving with readiness `DOWN` so load balancers stop routing |
| `SHUTDOWN_TIMEOUT_MS` | `10000` | then wait this long for in-flight requests before closing connections |
| `HTTP_READ_HEADER_TIMEOUT_MS` | `5000` | `http.Server.ReadHeaderTimeout` |
| `HTTP_READ_TIMEOUT_MS` | `10000` | `http.Server.ReadTimeout` |
| `HTTP_WRITE_TIMEOUT_MS` | `15000` | `http.Server.WriteTimeout`, must be greater than `REQUEST_TIMEOUT_MS` |
| `HTTP_IDLE_TIMEOUT_MS` | `60000` | `http.Server.IdleTimeout` |
| `HTTP_MAX_HEADER_BYTES` | `16384` | `http.Server.MaxHeaderBytes` (max 1 MiB) |
| `RATE_LIMIT_RPS` | `200` | token bucket refill rate, per caller |
| `RATE_LIMIT_BURST` | `400` | token bucket size, per caller |
| `RATE_LIMIT_MAX_KEYS` | `10000` | callers tracked per limiter (least recently used are evicted) |
| `AUTH_ENABLED` | `true` | disable only for local tests |
| `AUTH_ISSUER` | required when enabled | expected `iss` claim |
| `AUTH_AUDIENCE` | empty (not checked) | expected `aud` claim, e.g. `products-api` |
| `AUTH_JWKS_URL` | required when enabled | Keycloak JWKS endpoint |
| `AUTH_REQUIRED_ROLE` | `products-reader` | role required in `realm_access.roles` |
| `AUTH_CLOCK_LEEWAY_MS` | `30000` | tolerance for `exp`/`nbf` clock skew |
| `AUTH_JWKS_TIMEOUT_MS` | `2000` | timeout of each JWKS download |
| `AUTH_JWKS_REFRESH_INTERVAL_MS` | `3600000` | periodic JWKS refresh |
| `AUTH_JWKS_MIN_REFRESH_INTERVAL_MS` | `10000` | minimum gap between on-demand refreshes (unknown `kid`) and warm-up retries |
| `FAULT_INJECTION_ENABLED` | `false` | master switch; `FAULT_RULES` are ignored unless `true` |
| `FAULT_RULES` | empty | `id:type[:times]`, types `400,429,500,502,503,timeout` |
| `FAULT_TIMEOUT_MS` | `5000` | how long a `timeout` fault holds the response (bounded by `REQUEST_TIMEOUT_MS`) |
| `PROBLEM_TYPE_BASE_URL` | `https://contracts.grupomariposa.dev/problems/` | prefix of the problem `type` URI |
| `LOG_LEVEL` | `INFO` | `DEBUG`, `INFO`, `WARN`, `ERROR` |
| `HEALTHCHECK_TIMEOUT_MS` | `2000` | timeout of the `-healthcheck` probe |

With the defaults a stop needs up to `SHUTDOWN_DRAIN_DELAY_MS + SHUTDOWN_TIMEOUT_MS` (13 s), so set
Compose `stop_grace_period` (or Kubernetes `terminationGracePeriodSeconds`) above that.

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
environment through the lookup function passed to `config.Load`; the process environment is never
mutated. Only the property **keys** and their count are logged, never values, and the server
password is never logged.

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
| `CONFIG_SERVER_FAIL_FAST` | `false` | `true` aborts startup if the server stays unreachable; otherwise a JSON warning is logged and env/defaults are used |

## Design

```
cmd/products-api        signal handling and exit code only
internal/product        domain: Product, Market, Status, TaxCategory, Repository port, errors
internal/catalog        application service: input validation and lookup use case
internal/storage/memory in-memory Repository adapter with the platform seed data
internal/httpapi        transport: routing, handlers, DTOs, problem details, middleware
internal/auth           JWT verification (RS256, issuer, audience, expiry, role) against a JWKS cache
internal/ratelimit      bounded LRU of per-key token buckets
internal/fault          FAULT_RULES parsing and thread-safe per-id counters
internal/telemetry      JSON slog logger with traceId, Prometheus registry
internal/config         typed env reader, parsing and validation
internal/config/remote  Spring Cloud Config Server client, properties parser, layered lookup
internal/app            composition root, server lifecycle, drain and shutdown, healthcheck mode
```

- **Standard library router** ([ADR 0005](../docs/adr/0005-go-standard-library-router.md)). Go 1.22
  `ServeMux` patterns (`GET /products/{productId}`) cover method matching and path parameters.
  Middleware is plain `func(http.Handler) http.Handler`, so there is no framework to learn,
  upgrade or patch, and every piece is tested with `httptest`. External modules are limited to
  what the standard library lacks: JWT (`golang-jwt`), JWK parsing (`jwkset`), Prometheus,
  `x/time/rate`, and kin-openapi in tests.
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
  refreshed periodically and on unknown `kid` (rate limited). Readiness stays `DOWN` until the
  first successful download. Token problems (signature, `iss`, `aud`, `exp`, `nbf`, `alg`,
  unknown `kid`) are `401`, a missing role is `403`, and an unreachable JWKS or cancelled
  context is `503`, so callers retry instead of giving up.
- **Cancellation.** The request context (with its deadline) flows through the service into
  `Repository.FindByIDInMarket`, which checks `ctx.Err()`. A deadline maps to `503`, a client
  cancellation to `499`, never to another 4xx.
- **Shutdown.** On SIGTERM readiness turns `DOWN`, the server keeps serving for
  `SHUTDOWN_DRAIN_DELAY_MS`, then `Shutdown` waits up to `SHUTDOWN_TIMEOUT_MS` for in-flight
  requests. Background work (JWKS refresh) is stopped only after the server has stopped.
- **Tracing.** A valid W3C `traceparent` is honoured; otherwise a trace id is generated. The id is
  returned in `traceparent`, `X-Request-Id`, every log line and every problem body.
- **Swapping the in-memory repository for a database.** Consumers depend only on the
  `product.Repository` port. Add an adapter (for example `internal/storage/postgres`) that
  implements `FindByIDInMarket(ctx, id, market)`, passes `ctx` to the driver and returns
  `product.ErrNotFound` when there is no row for that product and market. Then change the one
  line in `internal/app` that builds the repository. Domain, service, transport and tests stay
  untouched; the memory adapter keeps serving unit tests.
