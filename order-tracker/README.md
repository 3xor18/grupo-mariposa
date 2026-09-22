# order-tracker

Mariposa Order Tracker is a responsive Flutter web PWA for looking up B2B orders processed by
`order-processor` (`contracts/http/order-processor.openapi.yaml`). The UI is in Spanish and uses
Material 3.

- **Buscar**: look up an order by `orderId` and see its status, client, lines, totals, the rejection
  reason with rule violations, or the failure details for `TECHNICAL_FAILURE`.
- **Pedidos**: list recent orders (`GET /orders`) with filter chips for status and market,
  "Cargar más" pagination and pull-to-refresh. Tapping an order opens its detail. On phones it
  opens as a new route. From 840 dp wide, the list and the detail sit side by side.

Every screen handles four states: loading, empty (not found / no results), success, and error.
Errors come with a retry when the failure is transient and show a support code (`traceId`) when
the API returns one.

## Architecture

```
lib/
  app/        composition root, bootstrap, responsive shell (NavigationBar / NavigationRail)
  core/       config, Result type, typed failures, problem+json mapping, http client + bearer
              interceptor, formatters (es-MX / es-CO / es-PE), theme tokens, shared widgets,
              strings (core/l10n/app_strings.dart), browser abstractions
  features/
    orders/   data (DTOs, mappers, OrdersApi, repository impl), domain (entities, repository
              port, SearchOrder / ListOrders use cases), presentation (blocs, pages, widgets)
    auth/     data (PKCE, OIDC client, token set, repository impl), domain, presentation
```

- DTOs mirror the contract exactly and are separate from the domain entities. The explicit
  mappers in `features/orders/data/mappers` convert between them. Unknown status values map to
  `OrderStatus.unknown`, so the app acts as a tolerant reader (v1 only adds fields). A missing
  required field becomes an `UnexpectedResponseFailure` instead of a crash.
- Errors are parsed as RFC 9457 `application/problem+json` and turned into a sealed `AppFailure`
  (`NotFound`, `Validation`, `Unauthorized`, `Forbidden`, `RateLimited` with `Retry-After`,
  `Server`, `Network`, `UnexpectedResponse`). The app branches on `status`/`code`, never on
  `detail`.
- **Dependency injection** uses `RepositoryProvider`, not `get_it`. The graph is small and built
  once in `AppDependencies.create`. Scope follows the widget tree, so pushed routes see the same
  instances. Tests override dependencies without a global service locator, which follows the
  "no global mutable state" rule.

## State management and late responses

The app uses `flutter_bloc`. Both `OrderSearchBloc` and the query events of `OrdersListBloc` are
registered with `restartable()` from `bloc_concurrency`. When a new search, filter change or
refresh arrives, the emitter of the in-flight handler is cancelled, so an older response that
arrives later can no longer emit. The newest search always wins. `OrdersListBloc` handles
"load more" with `droppable()` and tags each request with a `generation`. If a filter change or
refresh starts a new generation while a page is loading, that page is discarded. `bloc_test`
covers both races, with a delayed first request and a fast second one.

## Authentication (ADR 0003)

Authorization Code + PKCE (S256) against Keycloak realm `mariposa`, public client `order-tracker`:

1. "Iniciar sesión" generates a verifier and a `state` with `Random.secure`, derives the
   challenge with `crypto` (SHA-256, base64url), stores both in `sessionStorage` and redirects to
   `/realms/mariposa/protocol/openid-connect/auth`.
2. On load, `?code=&state=` is validated against the stored state and exchanged at the token
   endpoint with an `http` POST. The URL is then cleaned with `history.replaceState`.
3. Tokens live in memory and in `sessionStorage`. `AuthenticatedHttpClient` adds
   `Authorization: Bearer` and refreshes the token 30 s before it expires. Concurrent requests
   share a single refresh call. A failed refresh or a 401 ends the session and returns the user
   to the login screen.
4. "Cerrar sesión" clears the session and redirects to the end-session endpoint with
   `id_token_hint`.

Browser access goes through `package:web` and is isolated in `core/platform/web_browser.dart`
behind interfaces, so the rest of the code is unit-testable on the VM.

## Runtime configuration

At startup the app fetches `config.json` from the web root, with a cache-busting query:

```json
{ "apiBaseUrl": "/api", "keycloakUrl": "http://localhost:8180", "realm": "mariposa", "clientId": "order-tracker" }
```

The container renders it from environment variables when it starts, so the same image works in
any environment. `web/config.json` is only the default for `flutter run`.

| Variable | Default | Purpose |
|---|---|---|
| `API_BASE_URL` | `/api` | base URL the browser uses for the Orders API |
| `KEYCLOAK_URL` | `http://localhost:8180` | public Keycloak URL (also added to CSP `connect-src`) |
| `KEYCLOAK_REALM` / `KEYCLOAK_CLIENT_ID` | `mariposa` / `order-tracker` | OIDC settings |
| `ORDERS_API_URL` | `http://order-processor:8080` | upstream behind the same-origin `/api/` proxy |
| `NGINX_RESOLVER` | `127.0.0.11` | DNS used by the proxy (lazy resolution, so nginx starts before the API) |

## Container

`Dockerfile` is multi-stage: `flutter build web --release --no-web-resources-cdn` runs in
`ghcr.io/cirruslabs/flutter`, and the result is served by `nginxinc/nginx-unprivileged:alpine` as
uid 101 on port 8080 with a `HEALTHCHECK` on `/healthz`.

- **SPA fallback**: unknown paths serve `index.html`.
- **Caching**: `no-cache` (ETag revalidation) by default, including `index.html`,
  `flutter_service_worker.js` and `config.json`. Images and fonts are cached for one day.
- **Security headers**: CSP limited to `'self'` plus the Keycloak origin (and `'wasm-unsafe-eval'`
  for CanvasKit), `frame-ancestors 'none'`, `X-Content-Type-Options`, `Referrer-Policy`.
- **Compression**: gzip, including wasm.
- **API proxy**: `/api/*` is proxied to `${ORDERS_API_URL}/*`. If the upstream is unreachable, the
  proxy returns a problem+json `502 BAD_GATEWAY`.

```bash
docker build -t order-tracker .
docker run --rm -p 8090:8080 -e ORDERS_API_URL=http://host.docker.internal:8080 order-tracker
docker build --target test .
```

The last command runs `flutter analyze` and `flutter test` inside the build.

## PWA

`web/manifest.json` sets the name "Mariposa Order Tracker", theme color `#5B3FA8`, `any` and
`maskable` icons, and `standalone` display, so the app is installable. Flutter 3.44 no longer
generates a caching service worker: its `flutter_service_worker.js` only unregisters itself. A
custom `web/flutter_bootstrap.js` therefore registers `web/pwa_worker.js`, a network-first
app-shell worker that lets the app start offline and never caches `/api` responses.

## Tests

Flutter is not required locally. Run the suite in Docker:

```bash
docker run --rm -v "$PWD:/app" -w /app ghcr.io/cirruslabs/flutter:stable \
  sh -c "flutter pub get && flutter analyze && flutter test --coverage"
```

The suite covers:

- `bloc_test` for every bloc and cubit, including the late-response races
- DTO parsing and mappers: valid documents, missing optional fields, contract violations
- problem+json mapping
- repository tests with a mocked `http.Client` (mocktail)
- PKCE (RFC 7636 test vector), callback parsing, token expiry and single-flight refresh
- widget tests for every state and for phone, tablet and desktop layouts

Line coverage of `lib/` is 100%. `lib/main.dart` and `core/platform/web_browser.dart` are
excluded because they are the `package:web` bootstrap.

### E2E (Playwright)

`e2e/` holds a smoke suite that runs against the composed stack at `http://localhost:8090` with a
desktop and a mobile Chromium project. It logs in through the Keycloak form, searches
`ORD-MX-000147` (status "Aprobado", total 2,100.11), searches an unknown id to reach the empty
state, and opens the recent orders screen. The app turns on the semantics tree
(`SemanticsBinding.ensureSemantics`), so selectors use roles and accessible names.

```bash
cd e2e && npm ci && npx playwright install chromium
E2E_BASE_URL=http://localhost:8090 E2E_USERNAME=analyst E2E_PASSWORD=<DEMO_USER_PASSWORD> npx playwright test
```

## Known limitations

- Amounts are formatted with `intl` locale data. For `es_PE`, that data renders `S/ 2.100,11`
  rather than Peru's usual `S/ 2,100.11`.
- Line `discountRate` and `taxRate` are assumed to be fractions (0.16 = 16 %), because the
  contract does not specify the unit.
- The list uses explicit "Cargar más" pagination, not infinite scroll.
