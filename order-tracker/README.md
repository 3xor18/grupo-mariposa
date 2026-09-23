# order-tracker

Mariposa Order Tracker is a responsive Flutter web PWA for looking up B2B orders processed by
`order-processor` (`contracts/http/order-processor.openapi.yaml`). The UI is in Spanish and uses
Material 3.

- **Buscar**: look up an order by `orderId` and see its status, client, market, event version,
  lines, totals, the rejection reason with rule violations, or the failure details for
  `TECHNICAL_FAILURE`.
- **Pedidos**: list recent orders (`GET /orders`) with filter chips for status and market,
  "Cargar más" pagination and pull-to-refresh. Tapping an order opens its detail. On phones it
  opens as a new route. From 840 dp wide, the list and the detail sit side by side. The tab is
  built only when it is first opened.

Every screen handles four states: loading, empty (not found / no results), success, and error.
Errors come with a retry when the failure is transient and show a support code (`traceId`) when
the API returns one.

## Architecture

```
lib/
  app/        composition root, bootstrap, responsive shell (NavigationBar / NavigationRail)
  core/       config, Result type, typed failures, problem+json mapping, http client + bearer
              interceptor, Money value object, formatters, theme tokens, shared widgets,
              strings (core/l10n/app_strings.dart), browser abstractions
  features/
    orders/   data (DTOs, mappers, OrdersApi, repository impl), domain (entities, repository
              port, SearchOrder / ListOrders use cases), presentation (blocs, pages, widgets)
    auth/     data (PKCE, OIDC client, id_token validation, repository impl), domain, presentation
```

- **DTOs and mappers**: DTOs mirror the contract and are separate from the domain entities. The
  explicit mappers in `features/orders/data/mappers` convert between them.
  - Unknown statuses map to `unknown`, so the app acts as a tolerant reader (v1 only adds
    fields).
  - A missing required field, or an amount with more significant decimals than its currency
    allows, becomes an `UnexpectedResponseFailure` instead of a crash.
  - `eventVersion` is mapped and shown. `sourceEventId` is not used, so it is not mapped.
- **Market catalog (ADR 0006)**: markets are not an enum. `config.json` delivers the catalog, and
  the app reads it into `MarketCatalog`. Each market has a code, a currency, a locale and a
  display name, and each currency has its ISO 4217 fraction digits.
  - A market is a `MarketCode` value object.
  - Filter chips and labels are built from the catalog.
  - A code outside the catalog is displayed as it comes and never fails.
- **Money**: amounts are parsed from their decimal text into integer units, using the currency's
  fraction digits from the catalog, so formatting never touches binary floating point.
  - `Money` holds amounts and totals: 2 decimals for MXN, COP, PEN and USD, and 0 for CLP.
  - `UnitPrice` keeps line prices with up to 4 decimals and trims them to the currency digits for
    display.
  - A currency outside the catalog uses 2 decimals and shows its code.
- **Formatting**: `intl` locale data is used when it exists (`es-MX` gives `$2,100.11`). `intl`
  has no data for `es-CL`, `es-CO`, `es-PE` or `es-EC`. For those, the app falls back to Spanish
  separators with the symbol first: `$ 45.371` (CLP), `$ 1.234,50` (USD in Ecuador),
  `S/ 2.100,11` (PEN).
- **Errors**: problem+json responses (RFC 9457) become a sealed `AppFailure`. The app branches on
  `status` and `code`, never on `detail`.
- **Dependency injection** uses `RepositoryProvider`, not `get_it`.
  - The graph is small and built once in `AppDependencies.create`, which also disposes it.
  - Scope follows the widget tree, so pushed routes see the same instances.
  - Tests override dependencies without a global service locator, which follows the "no global
    mutable state" rule.

## State management and late responses

The app uses `flutter_bloc`. Both `OrderSearchBloc` and the query events of `OrdersListBloc` are
registered with `restartable()` from `bloc_concurrency`. When a new search, filter change or
refresh arrives, the in-flight handler is cancelled, so an older response that arrives later
cannot emit. The newest search always wins.

"Load more" uses `droppable()` and is ignored while a refresh is running. Every request carries a
`generation`. Each first-page load bumps it when it starts and again when it completes, so a page
requested before a filter change or refresh is dropped. Appended pages skip `orderId`s that are
already listed, which covers rows that shift between offset pages. Cursor (keyset) pagination
from `order-processor` is the next step to remove the shifting altogether.

## Authentication (ADR 0003)

The app uses Authorization Code + PKCE (S256) against the Keycloak realm, with the public client
`order-tracker`.

1. **Login.** "Iniciar sesión" generates a PKCE verifier, a `state` and a `nonce` with
   `Random.secure`. These go into `sessionStorage` only for the length of the redirect. The app
   then redirects to the authorization endpoint with the fixed `redirectUri` from runtime
   config.
2. **Callback.** On return, the app checks `state`, exchanges the code at the token endpoint and
   validates the `id_token` (`iss`, `aud`, `exp` and `nonce`). It then cleans the URL and removes
   the redirect keys.
3. **Token storage.** Access, refresh and id tokens live in memory only.
4. **Page load.** On every load, bootstrap tries a silent `prompt=none` authorization before the
   widget tree exists. It shows a splash with no Navigator, so Flutter's history setup cannot
   abort the redirect. If Keycloak answers `login_required`, the login screen appears without an
   error.
5. **Token refresh.** `AuthenticatedHttpClient` refreshes the token 30 s before it expires, and
   concurrent requests share one refresh call.
   - A rejected grant or an invalid refreshed `id_token` ends the session. A network or 5xx
     failure does not: the current token keeps being used until it expires, and after that the
     request fails as a retryable network error.
   - A session epoch discards refresh results that arrive after logout or after a 401.
   - A 401 ends the session only if the rejected token is still the current one.
6. **Logout.** "Cerrar sesión" clears the session and redirects to the end-session endpoint with
   `id_token_hint`.

Keycloak should enable refresh token rotation for this client. Browser access goes through
`package:web` and is isolated in `core/platform/web_browser.dart` behind interfaces.

## Runtime configuration

At startup the app fetches `config.json` from the web root, with a cache-busting query and a
network timeout:

```json
{
  "apiBaseUrl": "/api",
  "keycloakUrl": "http://localhost:8180",
  "realm": "mariposa",
  "clientId": "order-tracker",
  "redirectUri": "http://localhost:8090/",
  "enableSemantics": true,
  "markets": [
    { "code": "MX", "currency": "MXN", "locale": "es-MX", "name": "México" },
    { "code": "CL", "currency": "CLP", "locale": "es-CL", "name": "Chile" },
    { "code": "EC", "currency": "USD", "locale": "es-EC", "name": "Ecuador" }
  ],
  "currencies": { "MXN": 2, "CLP": 0, "USD": 2 }
}
```

The semantics tree (needed by screen readers and Playwright) is turned on only when
`enableSemantics` is true. `web/config.json` holds development values for `flutter run` and is
removed from the image. The container renders the real file at startup, so config-server
credentials never reach the browser.

### Spring Cloud Config Server

The hook `docker-entrypoint.d/10-order-tracker-config.envsh` runs
`/usr/local/bin/order-tracker-config` (`nginx/order-tracker-config.sh`). The nginx entrypoint
sources the hook, so the values it exports reach the template step. When `CONFIG_SERVER_URL` is
set, the script:

1. Fetches `${CONFIG_SERVER_URL}/${CONFIG_APP_NAME}-${CONFIG_PROFILE}.properties` with curl,
   using basic auth. The credentials go to curl on stdin (`--config -`), so they never show up in
   the process list or the logs.
2. Makes up to 4 attempts, each limited to `CONFIG_SERVER_TIMEOUT` seconds (default 5). It waits
   `CONFIG_SERVER_RETRY_DELAY` seconds between attempts, doubling each time (default 1, so 1 s,
   2 s, 4 s). curl errors are logged.
3. Parses Spring-flattened keys:
   - The first unescaped `=` or `:` is the separator, and both sides are trimmed.
   - Escapes such as `\:` are decoded.
   - Keys are uppercased with `.` and `-` turned into `_`.
   - Unknown keys are ignored.
4. Resolves each value with the precedence **explicit env var > config server > default**.
5. Validates every value against a strict pattern (http(s) URL, absolute path, name, host,
   boolean or number). Values with whitespace, control characters, `;`, `$` or quotes stop the
   container before they reach nginx or `config.json`.

When the server is unreachable, the container exits with a non-zero status. This is controlled by
`CONFIG_SERVER_FAIL_FAST`, which defaults to `true`; with `false`, the script warns and continues
with env vars and defaults. The image has no localhost defaults, and a missing required value
stops the container.

| Config server key | Variable | Default | Purpose |
|---|---|---|---|
| `api-base-url` | `API_BASE_URL` | `/api` | Orders API base for the browser (absolute origins join CSP) |
| `orders-api-url` | `ORDERS_API_URL` | required | upstream behind the `/api/` proxy |
| `keycloak.url` | `KEYCLOAK_URL` | required | public Keycloak URL (its origin joins CSP `connect-src`) |
| `keycloak.realm` | `KEYCLOAK_REALM` | `mariposa` | OIDC realm |
| `keycloak.client-id` | `KEYCLOAK_CLIENT_ID` | `order-tracker` | OIDC public client |
| `redirect-uri` | `REDIRECT_URI` | required | fixed OIDC redirect and post-logout URI |
| `enable-semantics` | `ENABLE_SEMANTICS` | `false` | turn on the semantics tree |
| `hsts-max-age` | `HSTS_MAX_AGE` | `0` (off) | HSTS max-age, for deployments behind TLS |
| — | `NGINX_RESOLVER` | `127.0.0.11` | DNS for the lazy proxy resolution |
| `platform.markets` | `PLATFORM_MARKETS` | required | `CODE:CURRENCY:LOCALE` list (`application.yml`) |
| `platform.currencies` | `PLATFORM_CURRENCIES` | required | `CODE:DIGITS` list (`application.yml`) |
| `market-names` | `MARKET_NAMES` | empty (code) | `CODE:Name` list of display names |

The script builds the `markets` and `currencies` members of `config.json` from these keys. It
fails when a market uses a currency without declared digits, and it rejects names that contain
quotes, backslashes, control characters or shell and nginx metacharacters.

| Config server variable | Default |
|---|---|
| `CONFIG_SERVER_URL` | empty (disabled) |
| `CONFIG_APP_NAME` / `CONFIG_PROFILE` | `order-tracker` / `default` (Compose uses `docker`) |
| `CONFIG_SERVER_USERNAME` / `CONFIG_SERVER_PASSWORD` | empty (no auth) |
| `CONFIG_SERVER_TIMEOUT` / `CONFIG_SERVER_RETRY_DELAY` | `5` / `1` seconds |
| `CONFIG_SERVER_FAIL_FAST` | `true` |

`nginx/tests/order-tracker-config.test.sh` checks this logic with a stub properties file and a
fake `curl`. It covers separators and escapes, precedence, required keys, retries and surfaced
errors, fail-fast, that the password is never exposed, validation, CSP sources and HSTS.

## Container

`Dockerfile` is multi-stage and pins both base images by tag and digest:
`ghcr.io/cirruslabs/flutter:3.44.0` and `nginxinc/nginx-unprivileged:1.31.6-alpine3.24`.

- **Gates**: the `test` stage (`flutter analyze` and `flutter test`) and the
  `runtime-config-test` stage (`shellcheck` and the shell tests) each leave a marker file. The
  runtime image copies both, so a failing test fails `docker build`.
- **Runtime**: nginx listens on port 8080 over IPv4 and IPv6, runs as uid 101 and has a
  `HEALTHCHECK` on `/healthz`.
- **SPA fallback**: unknown paths serve `index.html`.
- **Caching**: `no-cache` (ETag revalidation) by default, including `index.html` and
  `config.json`. Images and fonts are cached for one day.
- **Security headers**:
  - CSP limited to `'self'`, plus the Keycloak origin and any absolute API origin, and
    `'wasm-unsafe-eval'` for CanvasKit.
  - `frame-ancestors 'none'`, `Cross-Origin-Opener-Policy: same-origin`,
    `X-Content-Type-Options`, `Referrer-Policy`.
  - HSTS when `hsts-max-age > 0`.
- **API proxy**: `/api/*` is proxied to `${ORDERS_API_URL}/*`. If the upstream is unreachable,
  the proxy returns a problem+json `502 BAD_GATEWAY`. Its `instance` echoes the path only when
  the path is JSON-safe.

```bash
docker build -t order-tracker .
docker run --rm -p 8090:8080 -e ORDERS_API_URL=http://host.docker.internal:8080 \
  -e KEYCLOAK_URL=http://localhost:8180 -e REDIRECT_URI=http://localhost:8090/ order-tracker
docker run --rm -p 8090:8080 -e CONFIG_SERVER_URL=http://config-server:8888 \
  -e CONFIG_PROFILE=docker -e CONFIG_SERVER_USERNAME=reader \
  -e CONFIG_SERVER_PASSWORD=... order-tracker
```

## PWA

`web/manifest.json` makes the app installable:

- name "Mariposa Order Tracker"
- `any` and `maskable` icons
- `standalone` display
- theme color `#5B3FA8`, which is `AppColors.seed`. A test fails if the manifest or `index.html`
  drift from that single source.

Flutter 3.44 no longer generates a caching service worker, so `web/flutter_bootstrap.js`
registers `web/pwa_worker.js`. That worker is network-first and caches only an allow-list of
shell assets (navigations, `index.html`, bootstrap, `main.dart.js`, `assets/`, `canvaskit/`,
`icons/`). It never caches:

- requests with an `Authorization` header
- cross-origin requests
- anything under the runtime API base (read from `config.json`)
- `config.json` itself

## Tests

Flutter is not required locally. Run the suite in Docker:

```bash
docker run --rm -v "$PWD:/app" -w /app ghcr.io/cirruslabs/flutter:3.44.0 \
  sh -c "flutter pub get && flutter analyze && flutter test --coverage"
```

The suite covers:

- `bloc_test` for every bloc and cubit, including the late-response and pagination races. Tests
  use completers and `pumpEventQueue`, never fixed delays.
- DTO parsing, `Money` parsing and the mappers.
- problem+json mapping and repository tests with a mocked `http.Client` (mocktail).
- The auth flow:
  - PKCE (RFC 7636 test vector), silent sign-in, nonce and `id_token` validation.
  - Refresh outcomes and the session epoch.
  - 401 handling, and the absence of token storage.
- Widget tests for every state and for phone, tablet and desktop layouts.

Line coverage of `lib/` is 100%. `lib/main.dart` and `core/platform/web_browser.dart` are
excluded because they are the `package:web` bootstrap.

### E2E (Playwright)

`e2e/` holds a smoke suite with a desktop and a mobile Chromium project. It runs against the
composed stack, which must set `enable-semantics: true`. The suite:

- logs in through the Keycloak form
- searches `ORD-MX-000147` and checks the status label "Estado: Aprobado" and the total 2,100.11
- searches an unknown id and expects the "Pedido no encontrado" heading
- opens the recent orders screen

Locators use exact roles and names. Retries are 0 and tests run on a single worker.

```bash
cd e2e && npm ci && npx playwright install chromium
E2E_BASE_URL=http://localhost:8090 E2E_USERNAME=analyst E2E_PASSWORD=<DEMO_USER_PASSWORD> \
  E2E_REALM=mariposa E2E_BROWSER_CHANNEL=chrome npx playwright test
```

## Known limitations

- `intl` 0.20 has no number data for `es-CL`, `es-CO`, `es-PE` or `es-EC`. For those locales
  the app uses Spanish separators with the symbol first. That is right for Chile, Colombia and
  Ecuador, but Peru usually writes `S/ 2,100.11` rather than `S/ 2.100,11`.
- Amounts are formatted with the locale of the first catalog market that uses their currency, so
  USD always takes Ecuador's format.
- Line `discountRate` and `taxRate` are assumed to be fractions (0.16 = 16 %), because the
  contract does not specify the unit.
- Pagination is offset based. Duplicates are removed, but a row can still be skipped when newer
  orders shift the pages. Cursor pagination is the planned fix.
- With tokens in memory only, each page load costs a silent round trip to Keycloak. If Keycloak
  is unreachable at that moment, the browser shows its own error page.
