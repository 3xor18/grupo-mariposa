# Precios, descuentos e impuestos (TAX)

## Propósito

Calcular de forma determinista los importes de cada pedido aprobado (subtotal, descuento mayorista,
IVA y totales) y administrar las tasas de IVA con fecha de vigencia, cambiadas mediante una API con
aprobación de cuatro ojos y aplicadas según el momento en que ocurrió el pedido.

## Alcance

- Fórmula por línea y totales, descuento mayorista, IVA por mercado y categoría, clientes exentos,
  redondeo, selección de la tasa vigente, semilla y respaldo desde configuración, API
  `/tax-rates` (listar, proponer, aprobar, rechazar) y su auditoría.

## Fuera de alcance

- Elegibilidad del pedido (`order-processing.md`), definición de monedas y decimales
  (`markets.md`), autenticación del rol `orders-admin` (`security.md`).

## Requisitos

### REQ-TAX-001 Fórmula de cada línea

Para cada línea aprobada el sistema DEBE calcular, redondeando HALF_UP a los decimales de la moneda
en cada paso: `grossSubtotal = unitPrice × quantity`; `discount = grossSubtotal × discountRate`;
`netSubtotal = grossSubtotal − discount`; `taxAmount = netSubtotal × taxRate`;
`lineTotal = netSubtotal + taxAmount`. Cada línea DEBE guardar `discountRate` y `taxRate` aplicados.

- **Escenario: ejemplo dorado ORD-MX-000147, línea 1**
  - Dado `CLI-99821` (MX, `WHOLESALE`, `GENERAL`) con 24 × `PRD-001` (`STANDARD`) a 35.5
  - Cuando se evalúa
  - Entonces `grossSubtotal 852.00`, `discount 25.56`, `netSubtotal 826.44`, `taxRate 0.16`,
    `taxAmount 132.23`, `lineTotal 958.67`

Trazabilidad: `LinePricerTest.should_price_discounted_wholesale_line_of_golden_example`,
`LinePricerTest.should_round_half_up_at_every_step`.

### REQ-TAX-002 Totales como suma de importes redondeados

Los totales del pedido (`grossSubtotal`, `discount`, `netSubtotal`, `tax`, `grandTotal`) DEBEN ser
la suma de los importes de línea ya redondeados, sin volver a redondear.

- **Escenario: ejemplo dorado completo**
  - Dado `ORD-MX-000147` (muestra `01-approved-golden-mx.json`): 24 × `PRD-001` a 35.5 y
    12 × `PRD-008` a 82.0
  - Cuando se procesa
  - Entonces los totales son `1836.00 / 25.56 / 1810.44 / 289.67 / 2100.11` en MongoDB, en la API,
    en `orders.processed.v1` y en la PWA

Trazabilidad: `OrderEvaluatorTest.should_approve_golden_example_with_exact_totals`, `TotalsTest`,
`OrderProcessingIT.should_approve_golden_order_and_publish_contract_compliant_event`,
`order-processing.feature` (golden example), `order-tracker/e2e/tests/smoke.spec.ts`.

### REQ-TAX-003 Descuento mayorista por volumen

Una línea DEBE recibir `PRICING_WHOLESALE_DISCOUNT_RATE` (0.03) cuando el cliente es `WHOLESALE` y
la cantidad de **esa línea** es ≥ `PRICING_WHOLESALE_DISCOUNT_MIN_QUANTITY` (20). Un cliente
`RETAIL` NUNCA DEBE recibir descuento.

- **Escenario: umbral exacto**
  - Dado `CLI-10003` (MX, `WHOLESALE`, `EXEMPT`) con 20 × `PRD-001` y 19 × `PRD-003` a 10.0
  - Cuando se procesa
  - Entonces sólo la primera línea tiene descuento y los totales son
    `390.00 / 6.00 / 384.00 / 0.00 / 384.00`

Trazabilidad: `WholesaleVolumeDiscountPolicyTest`,
`PricingRulesTest.should_apply_discount_rule_...`, `order-processing.feature` (exempt client
scenario).

### REQ-TAX-004 IVA por mercado, categoría y régimen

La tasa DEBE ser la del mercado del pedido para la `taxCategory` del producto (`STANDARD`,
`REDUCED`, `EXEMPT`). Un cliente con `taxRegime=EXEMPT` DEBE pagar 0 en todas las líneas; `GENERAL`
y `SIMPLIFIED` pagan la tasa. Valores de configuración vigentes: MX 0.16/0.08/0.00, CO
0.19/0.05/0.00, PE 0.18/0.10/0.00, CL 0.19/0.19/0.00, EC 0.15/0.05/0.00.

- **Escenario: tasas por mercado**
  - Dado un producto `STANDARD` y uno `REDUCED` a 100.0 cada uno, cliente mayorista del mercado
  - Cuando se procesa en MX, CO y PE
  - Entonces `tax` es `24.00`, `24.00` y `28.00`
- **Escenario: cliente exento en Ecuador**
  - Dado `ORD-EC-000602` de `CLI-60002` (`EXEMPT`) (muestra `26-approved-ec-exempt-client.json`)
  - Cuando se procesa
  - Entonces `tax` es `0.00`

Trazabilidad: `MarketTaxPolicyTest`, `LinePricerTest.should_not_tax_exempt_client_even_with_...`,
`order-processing.feature` (outline de tasas), `OrderEvaluatorTest.should_price_every_catalog_...`.

### REQ-TAX-005 Precio unitario sin redondear

`unitPrice` DEBE conservarse tal como llegó (hasta 4 decimales) en el pedido y en la API; sólo los
importes calculados se redondean a los decimales de la moneda.

- **Escenario: precio con más decimales que la moneda**
  - Dado un cliente mayorista MX con 20 × `unitPrice 0.335` (`STANDARD`)
  - Cuando se tasa la línea
  - Entonces `grossSubtotal 6.70`, `discount 0.20`, `netSubtotal 6.50`, `taxAmount 1.04`,
    `lineTotal 7.54`, y `unitPrice` sigue siendo `0.335`

Trazabilidad: `LinePricerTest.should_round_half_up_at_every_step`,
`OrderCommandValidatorTest.should_accept_prices_within_limits`,
`order_dto_mapper_test.dart` ("should accept unit prices with four decimals").

### REQ-TAX-006 Tasa vigente según el momento del pedido

La tabla de IVA aplicada DEBE ser la vigente en `occurredAt`; si el evento no trae `occurredAt`, la
vigente en `receivedAt`; si el instante es anterior al inicio del cronograma, la del inicio. Un
periodo cubre `[validFrom, validTo)`. El pedido DEBE guardar y exponer `taxRateEffectiveFrom` (el
mayor `validFrom` entre las tres categorías aplicadas). `TECHNICAL_FAILURE` no tiene tasa (`null`).

- **Escenario: cambio de 16 % a 17 % el 2026-09-18T15:42:10.500Z**
  - Dado el cronograma MX `STANDARD` 16 % desde 2000-01-01 y 17 % desde 2026-09-18T15:42:10.500Z
  - Cuando `occurredAt` es 2026-09-18T15:42:10Z, falta, o es 1990-01-01
  - Entonces se aplica 16 % (efectivo 2000-01-01), 17 % (efectivo 15:42:10.500Z) y 16 %
- **Escenario: tasa aprobada por la API**
  - Dado MX `STANDARD` 0.2 aprobado desde `validFrom` (30 días en el futuro)
  - Cuando se procesan pedidos con `occurredAt` una hora después y una hora antes de `validFrom`
  - Entonces el primero tiene `taxRate 0.2` y `taxRateEffectiveFrom = validFrom`; el segundo
    `taxRate 0.16` y `taxRateEffectiveFrom` anterior

Trazabilidad: `OrderEnricherTaxRatesTest`,
`TaxRateScheduleTest.should_resolve_rates_by_instant_...`,
`order-processor/.../integration/TaxRatesApiIT.java`
(`should_apply_an_approved_rate_only_to_orders_that_occur_after_it_starts`), `tax-rates.feature`
(pedidos EC con `occurredAt` un segundo antes y una hora después de `validFrom`), ADR 0008.

### REQ-TAX-007 Cronograma válido y continuo

El cronograma aprobado NO DEBE tener periodos solapados para un mismo `(market, category)` y DEBE
cubrir sin huecos, desde `TAX_RATES_SEED_FROM` y con el último periodo abierto, las tres categorías
de **cada** mercado del catálogo. Un cronograma cargado que no cumpla NO DEBE reemplazar al vigente.

- **Escenario: datos solapados tras un cronograma válido**
  - Dado un refresco que cargó MX `STANDARD` 17 % desde 2027-01-01 (semilla cerrada en esa fecha)
  - Cuando el siguiente refresco trae además otro 17 % abierto desde 2027-01-01T00:00:01Z
  - Entonces se conserva el cronograma anterior y `orders_tax_rates_fallback` sigue en 0

Trazabilidad: `TaxRateScheduleTest` (`should_detect_overlaps_only_within_the_same_market_and_...`,
`should_require_continuous_coverage_for_every_catalog_market`),
`RefreshingTaxRateSourceTest.should_swap_in_the_approved_schedule_and_keep_it_on_invalid_data`.

### REQ-TAX-008 Semilla desde configuración

Al arrancar, por cada `(market, category)` del catálogo **sin ningún** periodo `APPROVED`, el
sistema DEBE insertar un periodo `APPROVED` abierto desde `TAX_RATES_SEED_FROM`
(2000-01-01T00:00:00Z) con la tasa `PRICING_TAX_<MKT>_<CAT>`, `proposedBy=system-seed` y motivo
"Initial rate from the platform configuration". NUNCA DEBE sobrescribir periodos existentes: después
de la semilla, cambiar `PRICING_TAX_*` no cambia la tasa vigente (sólo el respaldo).

- **Escenario: semilla parcial**
  - Dado que MX `STANDARD` ya tiene un periodo aprobado y MX `REDUCED`/`EXEMPT` no
  - Cuando se ejecuta la semilla
  - Entonces sólo se intentan insertar `REDUCED` y `EXEMPT`, abiertos desde `TAX_RATES_SEED_FROM`,
    `APPROVED` y revisados por `system-seed`; un duplicado concurrente no cuenta como insertado

Trazabilidad: `TaxRateSeedServiceTest` (3 pruebas).

### REQ-TAX-009 Respaldo y refresco del cronograma

Hasta el primer refresco exitoso, y si la base no responde al arrancar, los pedidos DEBEN tasarse
con la tabla de configuración (`orders_tax_rates_fallback = 1`). El cronograma DEBE refrescarse
desde MongoDB cada `TAX_RATES_REFRESH_INTERVAL` (30 s); un refresco fallido conserva el cronograma
anterior. Un fallo de la semilla NO DEBE impedir el arranque.

- **Escenario: semilla fallida**
  - Dado MongoDB rechazando la semilla
  - Cuando arranca el servicio
  - Entonces arranca, intenta el refresco y sigue con la tabla de configuración

Trazabilidad: `RefreshingTaxRateSourceTest` (`should_serve_the_configuration_until_the_first_...`,
`should_seed_then_refresh_at_startup_even_when_the_seed_fails`).

### REQ-TAX-010 Tabla de configuración completa al arrancar

`order-processor` NO DEBE arrancar si algún mercado del catálogo carece de alguna de las tres tasas
en `pricing.tax-rates`, o si una tasa o el descuento están fuera de `[0, 1]`.

- **Escenario: mercados del catálogo sin tasas**
  - Dado el catálogo de cinco mercados y `pricing.tax-rates` sólo con `MX`
  - Cuando se construye la tabla al arrancar
  - Entonces falla con "Every catalog market needs STANDARD, REDUCED and EXEMPT rates"
- **Escenario: categoría faltante**
  - Dado `MX` sólo con `STANDARD`
  - Cuando se construye la tabla
  - Entonces falla por tabla incompleta

Trazabilidad: `ConfigurationSupportTest.should_fail_startup_when_a_catalog_market_has_no_...`,
`PricingRulesTest.should_reject_rates_above_one_or_missing`.

### REQ-TAX-011 Proponer una tasa

`POST /tax-rates` (rol `orders-admin`) DEBE crear una propuesta `PROPOSED` con `id` UUIDv7,
`proposedBy` = `preferred_username` del token (o `sub`), `proposedAt` y `changeReason` recortado, y
responder `201`. DEBE validar y reportar juntos: `market` del catálogo, `category` requerida, `rate`
entre 0 y 1 con ≤ 4 decimales, `validFrom` requerido y futuro (salvo
`TAX_RATES_ALLOW_PAST_VALID_FROM=true`), `validTo` posterior a `validFrom`, `changeReason` no vacío;
fechas como instante ISO-8601. Un error DEBE responder `400 VALIDATION_ERROR` con `errors[]`.
Proponer NO DEBE cambiar ninguna tasa vigente.

- **Escenario: tasa fuera de rango**
  - Dado `olga` con `orders-admin`
  - Cuando propone CO `REDUCED` con `rate 1.5`
  - Entonces responde `400` y `errors[0].field = rate`

Trazabilidad: `TaxRateAdminServiceTest` (`should_store_valid_proposals_as_proposed`,
`should_collect_every_validation_error`, `should_accept_past_valid_from_when_allowed`),
`TaxRateWebMapperTest.should_build_commands_with_the_authenticated_proposer`, `TaxRatesApiIT`,
`tax-rates.feature` (invalid proposals are 400), `contracts/http/order-processor.openapi.yaml`.

### REQ-TAX-012 Aprobación de cuatro ojos

Aprobar DEBE requerir un usuario distinto de quien propuso. Si el aprobador es el proponente, DEBE
responder `403 FOUR_EYES_REQUIRED` sin cambios. Una aprobación válida DEBE responder `200` con
`status=APPROVED`, `reviewedBy` y `reviewedAt`.

- **Escenario: el proponente intenta aprobar**
  - Dado una propuesta MX `STANDARD` 0.2 de `olga`
  - Cuando `olga` la aprueba
  - Entonces responde `403` con `code=FOUR_EYES_REQUIRED`
  - Y cuando `auditor` la aprueba responde `200`, `status=APPROVED`, `reviewedBy=auditor`

Trazabilidad: `TaxRateApprovalsTest.should_enforce_four_eyes_and_pending_status`, `TaxRatesApiIT`,
`tax-rates.feature` (`admin` propone, `admin` recibe `403`, `auditor` aprueba), ADR 0008.

### REQ-TAX-013 Sólo se revisan propuestas pendientes

Aprobar o rechazar una tasa que no está `PROPOSED` DEBE responder `409 TAX_RATE_CONFLICT`. Un `id`
inexistente DEBE responder `404 TAX_RATE_NOT_FOUND`.

- **Escenario: segunda revisión**
  - Dado una propuesta CO `REDUCED` 0.06 ya rechazada
  - Cuando se intenta aprobar
  - Entonces responde `409 TAX_RATE_CONFLICT`; y aprobar el id `nope` responde `404`

Trazabilidad: `TaxRateApprovalsTest.should_enforce_four_eyes_and_pending_status`, `TaxRatesApiIT`,
`tax-rates.feature` (rejected proposal cannot be approved; `TR-DOES-NOT-EXIST` → `404`).

### REQ-TAX-014 La aprobación cierra el periodo anterior y exige continuidad

Al aprobar, todo periodo `APPROVED` abierto del mismo `(market, category)` que empieza antes DEBE
cerrarse en el `validFrom` del nuevo. Si el nuevo periodo solapa otro aprobado DEBE responder
`409` (`OVERLAP`); si dejaría un hueco o un final cerrado sin sucesor DEBE responder `409` (`GAP`).

- **Escenario: nueva tasa futura**
  - Dado MX `STANDARD` 16 % abierto desde 2000-01-01 (semilla)
  - Cuando `bob` aprueba la propuesta de `alice` de 17 % desde 2027-01-01
  - Entonces el 17 % queda aprobado y la semilla se cierra en 2027-01-01
- **Escenario: hueco**
  - Dado la misma semilla
  - Cuando se aprueba 17 % de 2027-01-01 a 2027-07-01 sin periodo posterior
  - Entonces se rechaza con `GAP`

Trazabilidad: `TaxRateApprovalsTest` (`should_approve_and_close_the_previous_open_period`,
`should_leave_closed_periods_untouched_when_filling_after_them`, `should_reject_overlaps_and_gaps`),
`tax-rates.feature` (el periodo abierto anterior queda con `validTo = validFrom` del nuevo).

### REQ-TAX-015 Rechazo de una propuesta

`POST /tax-rates/{id}/reject` (rol `orders-admin`) DEBE marcar la propuesta `REJECTED` con
`reviewedBy`/`reviewedAt` y responder `200`. El rechazo no exige cuatro ojos (el proponente puede
rechazar su propia propuesta) y NO DEBE refrescar el cronograma.

- **Escenario: rechazo por el revisor**
  - Dado una propuesta CO `REDUCED` 0.06 de `olga`
  - Cuando `auditor` la rechaza
  - Entonces responde `200` con `status=REJECTED`

Trazabilidad: `TaxRateAdminServiceTest.should_reject_through_the_repository_without_refreshing`,
`TaxRateApprovalsTest.should_enforce_four_eyes_and_pending_status`, `TaxRatesApiIT`,
`tax-rates.feature` (`auditor` rechaza).

### REQ-TAX-016 Efecto inmediato en la instancia que aprueba

Una aprobación DEBE refrescar el cronograma de la instancia que la atendió en la misma petición; las
demás instancias DEBEN verlo en el siguiente refresco (≤ `TAX_RATES_REFRESH_INTERVAL`), sin
reinicio.

- **Escenario: aprobación y refresco**
  - Dado una propuesta pendiente
  - Cuando se aprueba
  - Entonces el repositorio aplica los cambios y la fuente de tasas se refresca

Trazabilidad: `TaxRateAdminServiceTest.should_approve_through_the_repository_and_refresh_the_...`.

### REQ-TAX-017 Revisiones concurrentes serializadas

Cada revisión DEBE ejecutarse en una transacción MongoDB que toma un candado por
`(market, category)` (colección `tax_rate_guards`) y reemplaza cada documento sólo si su `version`
no cambió; si cambió DEBE responder `409 TAX_RATE_CONFLICT`. Un índice único parcial
`{market, category, validFrom}` sobre `APPROVED` DEBE impedir dos periodos aprobados con el mismo
inicio.

- **Escenario: dos aprobaciones simultáneas**
  - Dado dos propuestas del mismo `(market, category)`
  - Cuando dos administradores las aprueban a la vez
  - Entonces una se serializa detrás de la otra y ninguna deja el cronograma solapado

Trazabilidad: ⚠️ sin test (diseño en ADR 0008; índices creados por `IndexInitializer`)

### REQ-TAX-018 Listado de tasas

`GET /tax-rates` (rol `orders-admin`) DEBE listar periodos y propuestas con filtros opcionales
`market`, `category` y `status` (`PROPOSED`, `APPROVED`, `REJECTED`), ordenados por mercado,
categoría y `validFrom` descendente. Un filtro inválido DEBE responder `400`.

- **Escenario: pendientes de CO**
  - Dado una propuesta CO `REDUCED`
  - Cuando se lista con `market=CO&category=REDUCED&status=PROPOSED`
  - Entonces la propuesta aparece; y `status=LATER` responde `400`

Trazabilidad: `TaxRateWebMapperTest.should_parse_filters_and_report_invalid_values`,
`TaxRateAdminServiceTest.should_list_through_the_repository`, `TaxRatesApiIT`, `tax-rates.feature`.

### REQ-TAX-019 Auditoría de cada tasa

Cada documento de `tax_rates` DEBE conservar `market`, `category`, `rate`, `validFrom`, `validTo`,
`status`, `proposedBy`, `proposedAt`, `approvedBy`/`approvedAt` o `rejectedBy`/`rejectedAt`,
`changeReason` y `version`. Nunca se borran periodos: se cierran o se rechazan.

- **Escenario: ida y vuelta**
  - Dado una tasa en cada estado de revisión
  - Cuando se guarda y se lee
  - Entonces conserva todos los campos de auditoría

Trazabilidad: `TaxRateDocumentMapperTest.should_round_trip_every_review_state`,
`TaxRateApprovalsTest.should_require_audit_fields`.
