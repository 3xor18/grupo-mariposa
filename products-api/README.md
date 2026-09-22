# products-api

Product catalog per market (MX, CO, PE) for the B2B orders platform. Go standard library HTTP
server, contract in [`contracts/http/products-api.openapi.yaml`](../contracts/http/products-api.openapi.yaml),
errors per [ADR 0004](../docs/adr/0004-shared-error-contract.md).

## Run

```bash
go run ./cmd/products-api
AUTH_ENABLED=false go run ./cmd/products-api

docker build -t products-api .
docker run --rm -p 8081:8081 -e AUTH_ENABLED=false products-api
curl "http://localhost:8081/products/PRD-001?market=MX"
```

The binary doubles as its own container probe: `products-api -healthcheck` performs
`GET /health/live` on the local port and exits `0`/`1` (the distroless image has no shell or curl).

## Test and lint

```bash
go test ./... -race -coverprofile=coverage.out
go tool cover -func=coverage.out
golangci-lint run ./...
```

`-race` needs cgo (a C toolchain). Without one locally, run the suite in a container from the
repository root:

```bash
docker run --rm -v "$PWD:/work" -w /work/products-api golang:1.26 go test ./... -race
```

The contract tests load `../contracts` and validate real handler responses (200, every problem
status and health) against the OpenAPI document and `contracts/common/problem.schema.json`.

## Endpoints

| Method | Path | Auth | Notes |
|---|---|---|---|
| GET | `/products/{productId}?market=MX\|CO\|PE` | Bearer JWT with realm role | 200 product, problem otherwise |
| GET | `/health/live` | none | always `{"status":"UP"}` |
| GET | `/health/ready` | none | `503 {"status":"DOWN"}` while shutting down |
| GET | `/metrics` | none | Prometheus: `http_server_requests_total`, `http_server_request_duration_seconds` |

Error codes: `VALIDATION_ERROR` (400, with `errors[]`), `UNAUTHORIZED` (401), `FORBIDDEN` (403),
`PRODUCT_NOT_FOUND` (404, unknown product or not sold in that market), `RESOURCE_NOT_FOUND`
(404, unknown route), `RATE_LIMITED` (429 + `Retry-After`), `INTERNAL_ERROR` (500),
`BAD_GATEWAY` (502, fault injection only), `SERVICE_UNAVAILABLE` (503, request deadline or fault).

## Configuration

| Variable | Default | Description |
|---|---|---|
| `PORT` | `8081` | listen port |
| `REQUEST_TIMEOUT_MS` | `3000` | per-request deadline propagated down to the repository |
| `SHUTDOWN_TIMEOUT_MS` | `10000` | grace period to drain in-flight requests on SIGINT/SIGTERM |
| `RATE_LIMIT_RPS` | `200` | token bucket refill rate (per process) |
| `RATE_LIMIT_BURST` | `400` | token bucket size |
| `AUTH_ENABLED` | `true` | disable only for local tests |
| `AUTH_ISSUER` | required when enabled | expected `iss` claim |
| `AUTH_JWKS_URL` | required when enabled | Keycloak JWKS endpoint (cached, refreshed hourly and on unknown `kid`) |
| `AUTH_REQUIRED_ROLE` | `products-reader` | role required in `realm_access.roles` |
| `FAULT_RULES` | empty | `id:type[:times]`, types `400,429,500,502,503,timeout` |
| `FAULT_TIMEOUT_MS` | `5000` | how long a `timeout` fault holds the response (or until the client cancels) |
| `LOG_LEVEL` | `INFO` | `DEBUG`, `INFO`, `WARN`, `ERROR` |

Configuration is parsed once into a typed struct and validated as a whole; the process refuses
to start listing every invalid variable.

### Centralized configuration (Spring Cloud Config Server)

When `CONFIG_SERVER_URL` is set, startup fetches
`${CONFIG_SERVER_URL}/${CONFIG_APP_NAME}-${CONFIG_PROFILE}.properties` and maps each property to
its env-style name (uppercase, `.` and `-` become `_`): `rate-limit.rps` → `RATE_LIMIT_RPS`,
`auth.jwks-url` → `AUTH_JWKS_URL`. Parsing follows `java.util.Properties`: the first unescaped
`=`, `:` or whitespace separates key and value (so `auth.jwks-url: http://host:8080/...` keeps
the URL intact), `#`/`!` comments, `\:` `\=` `\\` `\uXXXX` escapes and line continuations.
Keys the service does not use (for example `management.*`) are ignored.

Precedence: **environment variable > config server > built-in default**. Remote values are
layered behind the environment through the lookup function passed to `config.Load`; the process
environment is never mutated. Values of keys containing `SECRET`, `PASSWORD`, `KEY` or `TOKEN`
are redacted in logs and the server password is never logged. `-healthcheck` resolves only
`PORT` from the environment (default `8081`): it never validates the rest of the configuration
nor calls the config server.

| Variable | Default | Description |
|---|---|---|
| `CONFIG_SERVER_URL` | empty (disabled) | base URL of the config server |
| `CONFIG_APP_NAME` | `products-api` | application name in the properties path |
| `CONFIG_PROFILE` | `default` | profile in the properties path (Compose uses `docker`) |
| `CONFIG_SERVER_USERNAME` | empty | HTTP basic auth user |
| `CONFIG_SERVER_PASSWORD` | empty | HTTP basic auth password |
| `CONFIG_SERVER_TIMEOUT_MS` | `3000` | timeout per attempt |
| `CONFIG_SERVER_RETRIES` | `3` | extra attempts on network errors, `429` and `5xx` |
| `CONFIG_SERVER_BACKOFF_MS` | `200` | first retry delay, doubled on each retry |
| `CONFIG_SERVER_FAIL_FAST` | `false` | `true` aborts startup if the server stays unreachable; otherwise a JSON warning is logged and env/defaults are used |

## Design

```
cmd/products-api        signal handling and exit code only
internal/product        domain: Product, Market, Status, TaxCategory, Repository port, errors
internal/catalog        application service: input validation and lookup use case
internal/storage/memory in-memory Repository adapter with the platform seed data
internal/httpapi        transport: routing, handlers, DTOs, problem details, middleware
internal/auth           JWT verification (RS256, issuer, expiry, realm role) against JWKS
internal/fault          FAULT_RULES parsing and thread-safe per-id counters
internal/telemetry      JSON slog logger with traceId, Prometheus registry
internal/config         env parsing and validation
internal/config/remote  Spring Cloud Config Server client, properties parser, layered lookup
internal/app            composition root, server lifecycle, graceful shutdown, healthcheck mode
```

- **Standard library router** ([ADR 0005](../docs/adr/0005-go-standard-library-router.md)). Go 1.22
  `ServeMux` patterns (`GET /products/{productId}`) cover method matching and path parameters.
  Middleware is plain `func(http.Handler) http.Handler`, so there is no framework to learn,
  upgrade or patch, and every piece is tested with `httptest`. External modules are limited to
  what the standard library lacks: JWT/JWKS, Prometheus, `x/time/rate`, and kin-openapi in tests.
- **Middleware order.** Global: trace context, access log and metrics, panic recovery. Product
  route: rate limit, authentication, fault injection, request deadline, handler. Faults run
  before the deadline so a `timeout` rule holds for `FAULT_TIMEOUT_MS` as the platform spec
  says, bounded only by client cancellation.
- **Cancellation.** The request context (with its deadline) flows through the service into
  `Repository.FindByIDInMarket`, which checks `ctx.Err()`. Deadline or cancellation maps to
  `503 SERVICE_UNAVAILABLE`, never to a 4xx.
- **Tracing.** A valid W3C `traceparent` is honoured; otherwise a trace id is generated. The id is
  returned in `traceparent`, `X-Request-Id`, every log line and every problem body.
- **Swapping the in-memory repository for a database.** Consumers depend only on the
  `product.Repository` port. Add an adapter (for example `internal/storage/postgres`) that
  implements `FindByIDInMarket(ctx, id, market)`, passes `ctx` to the driver and returns
  `product.ErrNotFound` when there is no row for that product and market. Then change the one
  line in `internal/app` that builds the repository. Domain, service, transport and tests stay
  untouched; the memory adapter keeps serving unit tests.
