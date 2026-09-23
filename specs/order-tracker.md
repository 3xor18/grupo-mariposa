# PWA order-tracker (UI)

## Propósito

`order-tracker` es una PWA Flutter web en español (Material 3) para que analistas consulten el
resultado de los pedidos procesados por `order-processor`: buscar uno por id y recorrer los
recientes, con estados claros, dinero exacto por moneda y autenticación OIDC con PKCE.

## Alcance

- Pantallas Buscar y Pedidos, estados de carga, carreras de respuestas, mapeo tolerante, dinero y
  formato por mercado, autenticación, configuración en tiempo de ejecución, contenedor nginx y PWA.

## Fuera de alcance

- La API consultada (`order-processing.md`), el realm de Keycloak (`security.md`).

## Requisitos

### REQ-UI-001 Buscar un pedido

La pantalla **Buscar** DEBE consultar `GET /orders/{orderId}` y mostrar estado, cliente, mercado,
versión del evento, líneas, totales, el motivo de rechazo con sus violaciones, o el detalle de
`failure` si es `TECHNICAL_FAILURE`.

- **Escenario: pedido dorado**
  - Dado `analyst` autenticado y `ORD-MX-000147` procesado
  - Cuando lo busca
  - Entonces ve "Estado: Aprobado" y el total `2,100.11`

Trazabilidad: `order-tracker/e2e/tests/smoke.spec.ts` (finds an approved order with its totals),
`order_search_bloc_test.dart`, `order_search_page_test.dart`.

### REQ-UI-002 Pedido inexistente

Un `404 ORDER_NOT_FOUND` DEBE mostrarse como estado vacío con el título "Pedido no encontrado", no
como error.

- **Escenario: id desconocido**
  - Dado un `orderId` que no existe
  - Cuando se busca
  - Entonces aparece el encabezado "Pedido no encontrado"

Trazabilidad: `smoke.spec.ts` (shows the empty state for an unknown order),
`order_search_bloc_test.dart` ("should emit not found when the api answers ORDER_NOT_FOUND").

### REQ-UI-003 Lista de pedidos recientes

La pantalla **Pedidos** DEBE listar `GET /orders` con chips de filtro por estado y por mercado,
"Cargar más" y *pull-to-refresh*; al tocar un pedido abre su detalle (nueva ruta en teléfonos, lado
a lado desde 840 dp). El tab DEBE construirse la primera vez que se abre.

- **Escenario: cambiar filtros**
  - Dado la lista cargada
  - Cuando se activa un chip de estado o de mercado
  - Entonces se recarga desde la primera página con el filtro

Trazabilidad: `orders_list_bloc_test.dart` ("should toggle status and market filters and reload
from the first page", "should append the next page"), `orders_list_page_test.dart`, `smoke.spec.ts`
(renders the recent orders screen).

### REQ-UI-004 Respuestas tardías y páginas desplazadas

La búsqueda más reciente DEBE ganar siempre (una respuesta vieja que llega tarde no se muestra); una
página pedida antes de un cambio de filtro o refresco DEBE descartarse; "Cargar más" DEBE ignorarse
durante un refresco o sin más páginas; los `orderId` ya listados NO DEBEN repetirse al anexar.

- **Escenario: respuesta vieja tardía**
  - Dado una búsqueda A en vuelo y luego una búsqueda B
  - Cuando A responde después que B
  - Entonces sólo se muestra B

Trazabilidad: `order_search_bloc_test.dart` ("should render only the newest search when an older
response arrives later"), `orders_list_bloc_test.dart` ("should discard a slow first page after the
filter changes", "should not repeat orders that shifted into the next page").

### REQ-UI-005 Cuatro estados y errores accionables

Cada pantalla DEBE manejar carga, vacío, éxito y error. Un error transitorio DEBE ofrecer reintento
y mostrar el `traceId` del problema como código de soporte. La app DEBE decidir por `status` y
`code` del problema, nunca por `detail`.

- **Escenario: reintento**
  - Dado una búsqueda que falló por red
  - Cuando el usuario reintenta
  - Entonces se repite la última búsqueda

Trazabilidad: `order_search_bloc_test.dart` ("should retry the last failed search"),
`failure_mapper_test.dart`, `failure_messages_test.dart`.

### REQ-UI-006 Lector tolerante del contrato

Un `status` desconocido DEBE mapearse a `unknown`; un campo requerido faltante o un importe con más
decimales significativos que su moneda DEBE convertirse en `UnexpectedResponseFailure`, nunca en un
fallo de la app.

- **Escenario: estado nuevo del servidor**
  - Dado un pedido con un `status` que esta versión no conoce
  - Cuando se mapea
  - Entonces se muestra como desconocido

Trazabilidad: `order_dto_mapper_test.dart` ("should map statuses unknown to this client version to
unknown", "should reject amounts with more than two decimals when mapping", "should reject documents
missing required members").

### REQ-UI-007 Dinero sin punto flotante

Los importes DEBEN parsearse desde su texto decimal a unidades enteras con los decimales de la
moneda del catálogo (2 para MXN, COP, PEN y USD; 0 para CLP). Los precios unitarios admiten hasta 4
decimales. Una moneda fuera del catálogo usa 2 decimales.

- **Escenario: moneda sin decimales**
  - Dado un total CLP `70480`
  - Cuando se parsea
  - Entonces se guarda como 70480 unidades sin decimales

Trazabilidad: `money_test.dart` ("should use the currency fraction digits for zero decimal
currencies", "should avoid binary floating point drift"), `unit_price_test.dart`.

### REQ-UI-008 Formato por mercado

Los importes DEBEN formatearse con el locale del mercado cuando `intl` tiene datos (`es-MX` →
`$2,100.11`) y, si no, con separadores en español y símbolo adelante: `$ 45.371` (CLP),
`$ 1.234,50` (USD en Ecuador), `S/ 2.100,11` (PEN). Las tasas se muestran como porcentaje.

- **Escenario: pesos chilenos**
  - Dado un importe CLP
  - Cuando se formatea
  - Entonces no lleva decimales y usa agrupación chilena

Trazabilidad: `app_formatters_test.dart` ("should format MXN with the Mexican locale data", "should
format CLP without decimals using Chilean grouping", "should format USD for Ecuador with two
decimals", "should format rates as percentages").

### REQ-UI-009 Autenticación OIDC con PKCE

El login DEBE usar Authorization Code + PKCE (S256) contra el cliente público `order-tracker`, con
`state` y `nonce` aleatorios en `sessionStorage` sólo durante la redirección; validar el `id_token`
(`iss`, `aud`, `exp`, `nonce`); guardar los tokens **sólo en memoria**; intentar un inicio
silencioso (`prompt=none`) en cada carga; refrescar 30 s antes de expirar con una sola llamada
compartida; y cerrar sesión en el endpoint de Keycloak con `id_token_hint`. Un 401 cierra la sesión
sólo si el token rechazado sigue siendo el actual.

- **Escenario: vector RFC 7636**
  - Dado el verificador de ejemplo del RFC 7636
  - Cuando se deriva el desafío
  - Entonces coincide con el `S256` publicado
- **Escenario: `id_token` ajeno**
  - Dado un `id_token` con otro `nonce`, audiencia, emisor o expirado
  - Cuando se valida
  - Entonces se rechaza

Trazabilidad: `auth_primitives_test.dart`, `oidc_client_test.dart`,
`auth_repository_impl_test.dart`, `authenticated_http_client_test.dart`, `auth_cubit_test.dart`,
`smoke.spec.ts` (login con Keycloak).

### REQ-UI-010 Configuración en tiempo de ejecución

La app DEBE cargar `config.json` al iniciar (con *cache busting* y timeout). El contenedor DEBE
generarlo desde el config server (`order-tracker-<perfil>.properties`) con precedencia entorno >
config server > defecto, validando cada valor con un patrón estricto (rechaza espacios, `;`, `$`,
comillas); DEBE abortar si falta un valor requerido o, con `CONFIG_SERVER_FAIL_FAST=true` (por
defecto), si el servidor no responde. Las credenciales del config server NUNCA llegan al navegador.

- **Escenario: valor peligroso**
  - Dado `ORDERS_API_URL='http://o:1;evil'`
  - Cuando arranca el contenedor
  - Entonces sale con estado 1 y reporta "ORDERS_API_URL has an invalid value"

Trazabilidad: `order-tracker/nginx/tests/order-tracker-config.test.sh`, `config_test.dart`.

### REQ-UI-011 Mercados desde el catálogo

Los chips y etiquetas de mercado DEBEN construirse desde el catálogo de `config.json` (generado
desde `platform.markets`, `platform.currencies` y `market-names`); un código fuera del catálogo DEBE
mostrarse tal cual sin fallar.

- **Escenario: cinco mercados**
  - Dado el catálogo con MX, CO, PE, CL y EC
  - Cuando se resuelve
  - Entonces incluye la moneda compartida USD y la moneda sin decimales CLP

Trazabilidad: `market_catalog_test.dart` ("should resolve five markets including shared and zero
decimal currencies", "should fall back for codes outside the catalog").

### REQ-UI-012 Proxy de la API y cabeceras de seguridad

nginx DEBE servir la SPA (rutas desconocidas → `index.html`), hacer proxy de `/api/*` a
`ORDERS_API_URL` y responder `502 BAD_GATEWAY` como `application/problem+json` si el upstream no
responde. DEBE enviar CSP limitada a `'self'`, el origen de Keycloak y el de la API absoluta, con
`frame-ancestors 'none'`, COOP `same-origin`, `X-Content-Type-Options`, `Referrer-Policy` y HSTS si
`HSTS_MAX_AGE > 0`; correr como uid 101 en el puerto 8080 con `HEALTHCHECK` en `/healthz`.

- **Escenario: API caída**
  - Dado `order-processor` detenido
  - Cuando la PWA llama a `/api/orders`
  - Entonces recibe `502` con `code=BAD_GATEWAY`

Trazabilidad: `order-tracker-config.test.sh` (fuentes CSP y HSTS); proxy `502` y cabeceras ⚠️ sin
test.

### REQ-UI-013 PWA instalable

`manifest.json` DEBE declarar el nombre "Mariposa Order Tracker", íconos `any` y `maskable`,
`display: standalone` y color `#5B3FA8` igual a `AppColors.seed`.

- **Escenario: manifiesto**
  - Dado la PWA publicada
  - Cuando se pide `/manifest.json`
  - Entonces responde con `name = "Mariposa Order Tracker"`

Trazabilidad: `smoke.spec.ts` (serves an installable PWA shell), `theme_color_source_test.dart`.

### REQ-UI-014 Service worker sin datos sensibles

El service worker (`web/pwa_worker.js`) DEBE ser *network-first* y cachear sólo el shell permitido;
NUNCA DEBE cachear solicitudes con `Authorization`, de otro origen, bajo la base de la API ni
`config.json`.

- **Escenario: llamada autenticada**
  - Dado una solicitud a `/api/orders` con token
  - Cuando pasa por el service worker
  - Entonces no se guarda en caché

Trazabilidad: ⚠️ sin test.
