# Catálogo de productos: products-api (PRD)

## Propósito

`products-api` (Go, `net/http` estándar, ADR 0005) es el dueño del catálogo de productos **por
mercado**: expone la lectura a `order-processor`, permite a administradores cambiar estado,
categoría de impuesto y nombre con concurrencia optimista, y anuncia cada cambio en
`products.changed.v1` con Transactional Outbox (ADR 0007).

## Alcance

- `GET` y `PATCH /products/{productId}?market=`, base `products`, outbox y relay, semilla, límites
  de tasa, plazo por solicitud, inyección de fallos, health, errores y guardas de producción.

## Fuera de alcance

- Caché en `order-processor` (`master-data-events-and-cache.md`), JWT (`security.md`), carga de
  configuración remota (`configuration.md`).

## Requisitos

### REQ-PRD-001 Lectura de un producto en un mercado

`GET /products/{productId}?market=` (rol `products-reader`) DEBE responder `200` con `productId`,
`name`, `sku`, `status`, `taxCategory` y `version`, y `ETag: "<version>"`. NO DEBE exponer costos,
proveedor ni stock. `HEAD` DEBE responder sin cuerpo.

- **Escenario: bebida en México**
  - Dado `PRD-001` con `market=MX`
  - Cuando `admin` lo consulta
  - Entonces responde `Bebida 600 ml`, `BEB-600-PET`, `ACTIVE`, `STANDARD` y `version` numérico

Trazabilidad: `products-api.feature` (returns a product), `products-api/internal/httpapi/`
`products_test.go` (`TestGetProductReturnsProduct`, `TestHeadProductHasNoBody`),
`contract_test.go` (`TestContractProductResponses`).

### REQ-PRD-002 Disponibilidad por mercado

Un producto existe por `(productId, market)`. Si el producto no existe o no se vende en ese mercado,
DEBE responder `404 PRODUCT_NOT_FOUND`.

- **Escenario: jugo sólo en México**
  - Dado `PRD-008`, que sólo existe en MX
  - Cuando se consulta con `market=PE`
  - Entonces responde `404` con `code=PRODUCT_NOT_FOUND`

Trazabilidad: `products-api.feature`, `products_test.go` (`TestGetProductNotFound`).

### REQ-PRD-003 Validación de entrada

`market` es obligatorio y DEBE pertenecer al catálogo; `productId` DEBE cumplir
`^PRD-[A-Z0-9]{1,20}$`. Una violación DEBE responder `400 VALIDATION_ERROR` con `errors[]`.

- **Escenario: tres entradas inválidas**
  - Dado `PRD-001?market=AR`, `PRD-001` sin `market` y `abc$?market=MX`
  - Cuando se consultan
  - Entonces las tres responden `400` con al menos un error de campo

Trazabilidad: `products-api.feature` (outline), `catalog/service_test.go` y
`httpapi/products_test.go` (`TestGetProductValidation`).

### REQ-PRD-004 Actualización administrativa con versión

`PATCH /products/{productId}?market=` (rol `products-admin`) DEBE aceptar al menos uno de `status`
(`ACTIVE`, `DISCONTINUED`), `taxCategory` (`STANDARD`, `REDUCED`, `EXEMPT`) y `name` (1 a 120
caracteres); campos desconocidos o no textuales DEBEN responder `400`. Un cambio efectivo DEBE
incrementar `version` y devolver el nuevo `ETag`; un cambio sin efecto DEBE responder `200` sin
incrementar la versión ni emitir evento. `If-Match` sigue las mismas reglas que `clients-api`
(ausente o `*` incondicional; fuertes `"N"` o `N`; débiles nunca coinciden; sin coincidencia
`412 PRECONDITION_FAILED`; mal formado `400`).

- **Escenario: descontinuar el producto demo**
  - Dado `PRD-020` en MX activo
  - Cuando `admin` envía `{"status":"DISCONTINUED"}` con el `ETag` vigente
  - Entonces responde `200`, `status=DISCONTINUED` y la versión sube en 1
- **Escenario: versión vieja**
  - Dado un `If-Match` que no coincide
  - Cuando se envía el `PATCH`
  - Entonces responde `412 PRECONDITION_FAILED`

Trazabilidad: `master-data-cache.feature` (discontinuing a cached product),
`httpapi/update_test.go` (`TestPatchUpdatesProductAndReturnsNewVersion`,
`TestPatchIfMatchVariants`, `TestNoOpPatchKeepsVersion`, `TestPatchStaleVersionReturns412`,
`TestPatchValidation`), `product/product_test.go` (`TestApplyWithSameValuesIsNoOp`).

### REQ-PRD-005 Cambio, outbox y relay

Un `PATCH` DEBE ejecutarse en una transacción MongoDB que relee el producto, valida `If-Match`,
incrementa `version` e inserta el evento `products.changed.v1` (UUIDv7, key `market:productId`) en
`outbox`. El relay DEBE reclamar sólo la versión no publicada más antigua por clave, con lease y
dueño, publicar con productor idempotente (`acks=all`), marcar `PUBLISHED` sólo si sigue siendo
dueño, y ante fallo volver a `PENDING` con `attempts` y `OUTBOX_RETRY_DELAY_MS` (1000). Los
publicados DEBEN expirar a los `OUTBOX_RETENTION` (7 d).

- **Escenario: de PATCH a Kafka**
  - Dado un `PATCH` válido
  - Cuando corre el relay
  - Entonces Kafka recibe un registro válido contra `products.changed.v1.schema.json` con key
    `MX:PRD-020`

Trazabilidad: `internal/app/integration_test.go` (`TestPatchPublishesChangeEventThroughOutbox`),
`storage/mongodb/outbox_test.go` (`TestClaimOldestVersionPerKeyInCreationOrder`,
`TestLeaseFencingAndLifecycle`, `TestExpiredLeaseIsReclaimed`), `outbox/relay_test.go`,
`catalog/service_test.go` (`TestChangeEventMatchesContract`), `master-data-cache.feature`.

### REQ-PRD-006 Semilla opcional

Con `SEED_ENABLED=true` las filas semilla DEBEN insertarse sólo si faltan (`$setOnInsert`); con
`false` (valor por defecto) no se siembra. `{productId, market}` DEBE ser un índice único.

- **Escenario: semilla deshabilitada**
  - Dado `SEED_ENABLED=false`
  - Cuando arranca el servicio
  - Entonces no inserta productos

Trazabilidad: `internal/app/app_test.go` (`TestSeedingIsOptIn`), `seed/products_test.go`.

### REQ-PRD-007 Límite de tasa por dirección y por principal

Cada solicitud a la ruta de productos DEBE pasar por un bucket por dirección IP (el par TCP; nunca
`X-Forwarded-For`) antes de autenticar y otro por principal (`azp`, si no `sub`) después
(`RATE_LIMIT_RPS` 200, `RATE_LIMIT_BURST` 400). El exceso DEBE responder `429 RATE_LIMITED` con
`Retry-After`.

- **Escenario: bucket agotado**
  - Dado un cliente que superó la ráfaga
  - Cuando vuelve a llamar
  - Entonces recibe `429` con `Retry-After`

Trazabilidad: `httpapi/guards_test.go` (`TestRateLimitPerClientAddress`,
`TestRateLimitPerPrincipal`, `TestRateLimitFallsBackToRawRemoteAddress`), `ratelimit/keyed_test.go`.

### REQ-PRD-008 Plazo por solicitud y cancelación

Cada solicitud DEBE tener un plazo `REQUEST_TIMEOUT_MS` (3000) propagado al repositorio: un plazo
vencido DEBE responder `503 SERVICE_UNAVAILABLE` y una cancelación del cliente `499`, nunca otro
`4xx`.

- **Escenario: timeout inyectado mayor al plazo**
  - Dado una regla `timeout` más larga que `REQUEST_TIMEOUT_MS`
  - Cuando se consulta el producto
  - Entonces responde `503`

Trazabilidad: `httpapi/products_test.go` (`TestGetProductHonoursRequestTimeout`),
`httpapi/faults_test.go` (`TestFaultTimeoutLongerThanDeadlineReturns503`,
`TestFaultTimeoutStopsWhenClientCancels`).

### REQ-PRD-009 Inyección de fallos sólo en lecturas y fuera de producción

`FAULT_RULES` DEBE aplicarse sólo con `FAULT_INJECTION_ENABLED=true` y sólo a lecturas; el `PATCH`
nunca se ve afectado. Reglas del perfil `docker`: `PRD-012:503:2`, `PRD-013:timeout`, `PRD-014:400`.

- **Escenario: recuperación tras N fallos**
  - Dado `PRD-012:503:2`
  - Cuando se consulta tres veces
  - Entonces responde `503`, `503` y `200`

Trazabilidad: `httpapi/faults_test.go` (`TestFaultInjectionRecoversAfterTimes`,
`TestFaultInjectionDisabledByDefault`), `httpapi/update_test.go`
(`TestPatchIsNotAffectedByFaultInjection`), `fault/fault_test.go`.

### REQ-PRD-010 Rutas y métodos desconocidos

Una ruta desconocida DEBE responder `404 RESOURCE_NOT_FOUND` con un `detail` estático (nunca refleja
la entrada); un método no soportado en una ruta conocida `405 METHOD_NOT_ALLOWED` con `Allow`. Todo
problema DEBE llevar `Cache-Control: no-store` y toda respuesta `X-Content-Type-Options: nosniff`.

- **Escenario: método no soportado**
  - Dado `DELETE /products/PRD-001?market=MX`
  - Cuando se envía
  - Entonces responde `405` con header `Allow`

Trazabilidad: `httpapi/products_test.go` (`TestUnknownPathReturnsStaticNotFound`,
`TestWrongMethodOnKnownRouteReturns405`), `observability_test.go`
(`TestSecurityHeadersOnEveryResponse`).

### REQ-PRD-011 Salud y apagado controlado

`/health/live` DEBE responder siempre `{"status":"UP"}`. `/health/ready` DEBE responder `503 DOWN`
mientras no haya llaves JWKS cargadas, MongoDB no responda o el servicio esté drenando. Ante
`SIGTERM` DEBE seguir sirviendo `SHUTDOWN_DRAIN_DELAY_MS` (3000) con readiness `DOWN` y luego
esperar hasta `SHUTDOWN_TIMEOUT_MS` (10 000).

- **Escenario: JWKS aún no cargado**
  - Dado que Keycloak no entregó llaves
  - Cuando se consulta `/health/ready`
  - Entonces responde `503`

Trazabilidad: `internal/app/app_test.go` (`TestReadinessWaitsForJWKSWarmUp`,
`TestGracefulShutdownDrainsWhileNotReady`, `TestShutdownTimeoutForcesClose`),
`products-api.feature` (health).

### REQ-PRD-012 Guardas de producción

Con `APP_ENV=production` el servicio NO DEBE arrancar con `STORAGE_DRIVER=memory`,
`SEED_ENABLED=true`, `FAULT_INJECTION_ENABLED=true` ni `KAFKA_TLS_ENABLED=false`.

- **Escenario: semilla en producción**
  - Dado `APP_ENV=production` y `SEED_ENABLED=true`
  - Cuando se carga la configuración
  - Entonces el arranque falla listando el error

Trazabilidad: `internal/config/config_test.go` (`TestLoadGuards`), `internal/app/app_test.go`
(`TestFaultInjectionIsGated`).
