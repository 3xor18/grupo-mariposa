# Catálogo de mercados y monedas (MKT)

## Propósito

Los mercados y monedas no están en el código: son un catálogo compartido en `config-repo` que los
cuatro componentes validan al arrancar (ADR 0006). Define qué pedidos se aceptan, qué moneda
corresponde a cada mercado y con cuántos decimales se redondea y se muestra el dinero.

## Alcance

- Gramática y validación de `platform.markets` / `platform.currencies`, pertenencia al catálogo,
  moneda por mercado, decimales por moneda, contratos con `pattern` y alta de un país.

## Fuera de alcance

- Tasas de IVA por mercado (`pricing-and-taxes.md`), presentación en la PWA (`order-tracker.md`).

## Requisitos

### REQ-MKT-001 Catálogo único compartido

El catálogo DEBE definirse en `config-repo/application.yml` como `platform.markets`
(`CODIGO:MONEDA:LOCALE`) y `platform.currencies` (`MONEDA:DECIMALES`), con las variables
`PLATFORM_MARKETS` / `PLATFORM_CURRENCIES` como sobrescritura. El valor vigente DEBE ser
`MX:MXN:es-MX,CO:COP:es-CO,PE:PEN:es-PE,CL:CLP:es-CL,EC:USD:es-EC` y
`MXN:2,COP:2,PEN:2,CLP:0,USD:2`.

- **Escenario: catálogo por defecto**
  - Dado el catálogo por defecto
  - Cuando cualquier servicio arranca
  - Entonces reconoce cinco mercados; `CLP` con 0 decimales y `USD` compartible

Trazabilidad: `MarketTest.should_resolve_currency_and_precision_per_market`,
`clients-api/src/shared/markets/market-catalog.spec.ts`
(`should_parse_the_default_five_market_...`), `products-api/internal/market/catalog_test.go`
(`TestParseDefaultCatalog`), `order-tracker/test/core/markets/market_catalog_test.dart`.

### REQ-MKT-002 Gramática y validación al arrancar

Los cuatro componentes DEBEN aplicar la misma gramática: entradas separadas por coma y campos por
`:` con espacios recortados; mercado `^[A-Z]{2}$`, moneda `^[A-Z]{3}$`, locale
`^[a-z]{2}-[A-Z]{2}$`; decimales de 0 a 4; sin mercados ni monedas duplicados; la moneda de cada
mercado declarada en `platform.currencies`. Un catálogo inválido DEBE impedir el arranque.

- **Escenario: moneda sin declarar**
  - Dado `PLATFORM_MARKETS=MX:MXN:es-MX,CL:CLP:es-CL` y `PLATFORM_CURRENCIES=MXN:2`
  - Cuando arranca `order-processor`
  - Entonces falla al arrancar
- **Escenario: locale mal formado**
  - Dado `MX:MXN:es_MX`
  - Cuando arranca
  - Entonces falla al arrancar

Trazabilidad: `ConfigurationSupportTest.should_fail_fast_on_malformed_platform_catalog`,
`MarketTest.should_reject_invalid_catalogs`, `catalog_test.go` (`TestParseRejectsInvalidCatalogs`),
`market-catalog.spec.ts`, `currency-catalog.spec.ts`, `market_catalog_test.dart`,
`order-tracker/nginx/tests/order-tracker-config.test.sh`.

### REQ-MKT-003 Mercado fuera del catálogo

Un pedido con `market` fuera del catálogo DEBE ir a `orders.processing.dlt` como `VALIDATION` y NO
DEBE procesarse con una tasa inventada ni persistirse. `products-api` DEBE responder
`400 VALIDATION_ERROR` a `?market=` fuera del catálogo.

- **Escenario: Argentina**
  - Dado `ORD-AR-000701` con `market=AR`, `currency=ARS` (muestra `25-invalid-unknown-market-ar`)
  - Cuando se procesa
  - Entonces la DLT recibe el mensaje con `VALIDATION` y `x-event-id`, y `GET /orders/<id>` da `404`
- **Escenario: producto en mercado desconocido**
  - Dado `GET /products/PRD-001?market=AR`
  - Cuando se consulta con `products-reader`
  - Entonces responde `400` con `errors[]`

Trazabilidad: `markets.feature` (unknown market), `products-api.feature` (unsupported market),
`OrderProcessingIT.should_dead_letter_orders_for_markets_outside_the_catalog`,
`OrderCreatedListenerTest.should_reject_markets_outside_the_catalog_as_validation_errors`.

### REQ-MKT-004 Moneda del mercado y monedas compartidas

La `currency` de un pedido DEBE ser exactamente la del mercado en el catálogo; si no, es
`VALIDATION`. Varios mercados PUEDEN compartir moneda (EC usa `USD`).

- **Escenario: Ecuador en dólares**
  - Dado `ORD-EC-000601` de `CLI-60001` con 30 × `PRD-018` a 1.25 y 5 × `PRD-019` a 8.4
  - Cuando se procesa con `currency=USD`
  - Entonces se aprueba con `79.50 / 1.13 / 78.37 / 2.10 / 80.47`

Trazabilidad: `markets.feature` (Ecuador), `MarketTest.should_share_a_currency_between_markets`,
`OrderCommandValidatorTest.should_reject_currency_that_does_not_match_market`.

### REQ-MKT-005 Decimales de la moneda en cálculo y serialización

Todos los importes DEBEN redondearse a los decimales de la moneda del pedido y serializarse con esa
escala: una moneda de 0 decimales DEBE viajar como entero JSON en la API y en
`orders.processed.v1`.

- **Escenario: Chile en pesos enteros**
  - Dado `CLI-50001` con 24 × `PRD-015` a 1990 y 10 × `PRD-016` a 1290 CLP
  - Cuando se procesa
  - Entonces los totales son `60660 / 1433 / 59227 / 11253 / 70480` y el JSON de la API contiene
    enteros (nunca `70480.0`)
- **Escenario: Chile en el evento de salida**
  - Dado 24 × `PRD-001` a 1990 CLP
  - Cuando se publica `orders.processed.v1`
  - Entonces el texto contiene `"grossSubtotal":47760`, `"discount":1433`, `"tax":8802` y
    `"grandTotal":55129`

Trazabilidad: `markets.feature` (Chile),
`OrderProcessingIT.should_price_chilean_orders_in_whole_...`,
`OrderEvaluatorTest.should_round_chilean_pesos_to_integers`, `MoneyTest`.

### REQ-MKT-006 Contratos sin enumeraciones de mercado

Los contratos DEBEN declarar `market` y `currency` con `pattern` (`^[A-Z]{2}$`, `^[A-Z]{3}$`) y no
con `enum`, para que un país nuevo no sea un cambio de contrato. El paso de `enum` a `pattern` quedó
aceptado como compatible en la lista de excepciones de oasdiff.

- **Escenario: esquema de entrada**
  - Dado `contracts/events/orders.created.v1.schema.json`
  - Cuando se valida un evento con `market=CL`
  - Entonces el esquema lo acepta; la pertenencia al catálogo la decide el servicio

Trazabilidad: `contracts/events/*.schema.json`, `contracts/http/*.openapi.yaml`,
`contracts/oasdiff/err-ignore.txt`, `scripts/ci/check-contracts.sh`.

### REQ-MKT-007 Alta de un país sin código

Agregar un país DEBE requerir sólo: la entrada en `platform.markets` (y su moneda en
`platform.currencies` si es nueva), las tres tasas `PRICING_TAX_<MKT>_*` en `config-repo`, los datos
de clientes y productos, y un reinicio progresivo. Las APIs DEBEN cargar semilla sólo de mercados
del catálogo.

- **Escenario: semilla filtrada por catálogo**
  - Dado un catálogo con sólo `MX` y `CL`
  - Cuando `clients-api` siembra datos
  - Entonces inserta 8 clientes (MX y CL), omite 6, `CLI-50001` queda en `version 1` y `CLI-20001`
    (CO) no existe

Trazabilidad: `mongo-persistence.e2e-spec.ts`
(`should_insert_only_catalog_markets_with_version_one`),
`ConfigurationSupportTest.should_fail_startup_when_a_catalog_market_has_no_complete_tax_rates`, ADR
0006.
