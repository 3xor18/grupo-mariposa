# Procesamiento de pedidos (OP)

## Propósito

`order-processor` convierte cada evento `orders.created.v1` en exactamente un resultado de negocio
(`APPROVED` o `REJECTED`) o en un `TECHNICAL_FAILURE` explícito y recuperable, lo persiste en
MongoDB, lo anuncia en `orders.processed.v1` y lo expone por la API de consulta `GET /orders`.

## Alcance

- Lectura y validación del evento de entrada, enriquecimiento con `clients-api` y `products-api`,
  elegibilidad, idempotencia y versiones, transacción inbox + orders + outbox, reintentos, DLT,
  `TECHNICAL_FAILURE`, relay del outbox y API de consulta de pedidos.

## Fuera de alcance

- Cálculo de importes e impuestos (`pricing-and-taxes.md`), catálogo de mercados (`markets.md`),
  caché de datos maestros (`master-data-events-and-cache.md`), forma exacta de los contratos
  (`events-and-contracts.md`) y autenticación (`security.md`).

## Requisitos

### REQ-OP-001 Lectura estricta del mensaje

El listener DEBE leer el valor como bytes y mapearlo sin coerciones (un texto no es número, un
decimal no es entero). Un payload vacío, que no es JSON, que no es un objeto o con un tipo
incorrecto DEBE ir a `orders.processing.dlt` con `x-error-category=DESERIALIZATION`, sin reintentos
y sin persistir nada. Los campos desconocidos DEBEN ignorarse (*tolerant reader*).

- **Escenario: payload ilegible**
  - Dado el valor `{"orderId":"ORD-MX-E2EV…RAW", this is not json` en `orders.created.v1`
  - Cuando el listener lo consume
  - Entonces la DLT recibe el mismo valor byte a byte con `DESERIALIZATION` y no hay bucle de
    reintentos
- **Escenario: cantidad fraccionaria**
  - Dado un ítem con `"quantity": 24.5` (muestra `21-invalid-missing-fields.json`)
  - Cuando se lee el mensaje
  - Entonces termina en la DLT como `DESERIALIZATION`

Trazabilidad: `validation.feature` (unreadable payload), `OrderMessageReaderTest`,
`OrderProcessingIT.should_send_unreadable_payload_to_dlt_without_poison_pill_loop`,
`OrderProcessingIT.should_reject_fractional_quantity_as_deserialization_error`.

### REQ-OP-002 Validación de contrato antes de cualquier consulta

Antes de llamar a otro servicio, el sistema DEBE validar: `eventId` y `orderId` de 1 a 64
caracteres; `clientId` con `^CLI-[A-Z0-9]{1,20}$`; `channel` ≤ 32; `market` del catálogo;
`currency` ISO de tres letras igual a la del mercado; `eventVersion` ≥ 1 (1 si falta); `occurredAt`
ISO-8601 con offset (opcional); de 1 a 500 ítems; `productId` con `^PRD-[A-Z0-9]{1,20}$` y único en
el pedido; `quantity` entero > 0; `unitPrice` ≥ 0, ≤ 1 000 000 000 000, ≤ 4 decimales y ≤ 18
dígitos. Una violación DEBE ir a la DLT como `VALIDATION`, sin reintentos, reportando **todos** los
errores en `x-error-cause`, y NO DEBE crear pedido.

- **Escenario: moneda de otro mercado**
  - Dado `ORD-MX-000151` con `market=MX` y `currency=COP` (muestra `10-invalid-currency-mismatch`)
  - Cuando se procesa
  - Entonces la DLT recibe el mensaje con `VALIDATION` y `GET /orders/ORD-MX-000151` responde `404`
- **Escenario: producto repetido**
  - Dado `ORD-MX-000152` con `PRD-001` dos veces
  - Cuando se procesa
  - Entonces termina en la DLT como `VALIDATION`

Trazabilidad: `validation.feature` (5 casos), `OrderCommandValidatorTest`,
`OrderCreatedListenerTest.should_fail_validation_without_calling_use_case`,
`OrderProcessingIT.should_send_contract_violations_to_dlt_with_original_bytes_and_persist_nothing`.

### REQ-OP-003 Reglas de elegibilidad y motivo principal

El sistema DEBE evaluar, en este orden: cliente inexistente (`CLIENT_NOT_FOUND`, sin más chequeos de
cliente); cliente no `ACTIVE` (`CLIENT_NOT_ACTIVE`); cliente de otro mercado
(`CLIENT_MARKET_MISMATCH`); y por cada ítem, producto inexistente en el mercado
(`PRODUCT_NOT_FOUND`) o no `ACTIVE` (`PRODUCT_NOT_ACTIVE`) con su `productId`. Con al menos una
violación el pedido DEBE quedar `REJECTED`, con **todas** las violaciones en `violations` y `reason`
igual al código de la primera.

- **Escenario: cliente bloqueado**
  - Dado `CLI-20002` (CO, `BLOCKED`) pidiendo 5 × `PRD-001` a 10.0 COP
  - Cuando se procesa
  - Entonces el pedido es `REJECTED`, `reason=CLIENT_NOT_ACTIVE` y se publica con ese `reason`
- **Escenario: cliente de otro mercado**
  - Dado `CLI-99821` (MX) en un pedido `PE`/`PEN`
  - Cuando se procesa
  - Entonces `reason=CLIENT_MARKET_MISMATCH`

Trazabilidad: `order-processing.feature` (outline R1–R5), `EligibilityPolicyTest`,
`OrderProcessingIT.should_reject_with_every_violation_and_publish_rejection`.

### REQ-OP-004 Un 404 de una dependencia es rechazo de negocio

Un `404` de `clients-api` o de `products-api` DEBE interpretarse como entidad inexistente
(`CLIENT_NOT_FOUND` / `PRODUCT_NOT_FOUND`), nunca como fallo técnico ni reintento.

- **Escenario: producto que no existe en el mercado**
  - Dado `CLI-30002` (PE) pidiendo `PRD-008`, que sólo existe en MX
  - Cuando `products-api` responde `404` para `market=PE`
  - Entonces el pedido es `REJECTED` con `PRODUCT_NOT_FOUND`

Trazabilidad: `HttpAdaptersTest.should_treat_404_as_business_not_found`,
`OrderProcessingIT.should_reject_when_product_does_not_exist_in_market`, `order-processing.feature`
(R4, R5).

### REQ-OP-005 Forma de un pedido rechazado

Un pedido `REJECTED` DEBE guardar sus líneas sin importes (con nombre, SKU y categoría si el
producto existe) y totales en cero con los decimales de la moneda. NO DEBE calcular dinero.

- **Escenario: rechazo sin importes**
  - Dado un pedido con cliente inexistente
  - Cuando se evalúa
  - Entonces `totals` son `0.00` y cada línea tiene `grossSubtotal`, `taxAmount` y `lineTotal` nulos

Trazabilidad: `OrderEvaluatorTest.should_reject_with_zero_totals_and_unpriced_lines`,
`ModelInvariantsTest.should_build_unpriced_lines_with_known_product_data`.

### REQ-OP-006 Un `eventId` produce un solo efecto

Recibir varias veces el mismo `eventId` (reentrega, réplica, carrera entre hilos o instancias) DEBE
producir un único pedido y un único evento `orders.processed.v1`. La garantía es el `_id` único de
`inbox` dentro de la transacción; la consulta previa es sólo un atajo.

- **Escenario: diez entregas del mismo evento**
  - Dado el mismo payload de `ORD-MX-E2EI…DUP` publicado 10 veces
  - Cuando termina el procesamiento
  - Entonces el pedido está `APPROVED` y `orders.processed.v1` tiene exactamente 1 registro
- **Escenario: ocho hilos**
  - Dado el mismo evento procesado por 8 hilos a la vez
  - Cuando todos terminan
  - Entonces hay un solo documento en `orders`, uno en `inbox` y uno en `outbox`

Trazabilidad: `idempotency.feature`, `ConcurrencyIT.should_persist_exactly_one_effect_when_...`,
`OrderProcessingIT.should_produce_single_effect_when_same_event_is_delivered_twice`,
`ProcessOrderServiceTest.should_short_circuit_duplicate_event_found_in_inbox`.

### REQ-OP-007 Conflicto de versión: gana el primero

Si llega un evento con el mismo `orderId` y `eventVersion` que un resultado ya decidido pero con
otro `eventId`, el sistema NO DEBE modificar el pedido y DEBE enviar el segundo evento a la DLT con
`VERSION_CONFLICT`, sin reintentos.

- **Escenario: muestra 18**
  - Dado `ORD-MX-000147` v1 aprobado con `grandTotal 2100.11`
  - Cuando llega otro evento v1 (`…TQ18`) con 1 × `PRD-001`
  - Entonces la DLT recibe `…TQ18` con `VERSION_CONFLICT` y el pedido conserva el `sourceEventId`
    original y `grandTotal 2100.11`

Trazabilidad: `idempotency.feature`,
`ConcurrencyIT.should_let_first_event_win_when_versions_collide`,
`VersionArbiterTest.should_flag_conflict_when_same_version_has_another_event`,
`OrderCreatedListenerTest.should_turn_version_conflicts_into_non_retryable_failures`.

### REQ-OP-008 Versión mayor reemplaza, versión menor se ignora

Un `eventVersion` mayor al guardado DEBE reemplazar el pedido y publicar un nuevo evento. Un
`eventVersion` menor DEBE ignorarse sin cambios ni evento de salida, registrándose en `inbox` con
resultado `STALE`.

- **Escenario: v1, v2, v1 obsoleta y v3**
  - Dado `ORD-MX-E2EI…VER` v1 aprobado
  - Cuando llegan v2 (48 × `PRD-001` a 35.5), una v1 obsoleta (999 × 1.0) y v3 (30 × 35.5)
  - Entonces el pedido queda con el `sourceEventId` de v3 y `orders.processed.v1` contiene sólo los
    eventos de v1, v2 y v3

Trazabilidad: `idempotency.feature`,
`OrderProcessingIT.should_ignore_lower_version_after_higher_one`,
`ProcessOrderServiceTest.should_ignore_stale_version_and_record_it_in_inbox`,
`VersionArbiterTest.should_mark_lower_incoming_version_as_stale`.

### REQ-OP-009 Transacción única inbox + orders + outbox

El resultado DEBE persistirse en **una** transacción MongoDB que inserta `inbox`, hace un upsert
condicional de `orders` (versión menor, o misma versión y mismo evento en `TECHNICAL_FAILURE`) e
inserta el evento en `outbox`. Un `DuplicateKey` DEBE revertir la transacción y clasificarse como
duplicado, obsoleto o conflicto. Los errores transitorios de MongoDB DEBEN reintentarse hasta
`MONGO_TX_ATTEMPTS` (5) con backoff `MONGO_TX_RETRY_BACKOFF` (20 ms) y jitter.

- **Escenario: carrera en el upsert**
  - Dado que otra instancia guardó una versión mayor entre la lectura y la escritura
  - Cuando el upsert condicional falla con `DuplicateKey`
  - Entonces la transacción se revierte y el resultado se clasifica `STALE`

Trazabilidad: `ProcessOrderServiceTest` (`should_report_duplicate_when_inbox_insert_loses_the_race`,
`should_classify_stale_when_higher_version_wins_during_processing`), `TransactionRunnerTest`,
`PersistenceIT`, ADR 0002.

### REQ-OP-010 Confirmación de offsets

El consumidor DEBE usar `enable.auto.commit=false`, `AckMode.RECORD` e
`isolation.level=read_committed`, y confirmar el offset sólo después del commit en MongoDB o de
publicar en la DLT. Si la DLT no está disponible, el registro NO DEBE confirmarse.

- **Escenario: DLT caída**
  - Dado un mensaje inválido y la DLT no disponible
  - Cuando el recuperador intenta publicarlo
  - Entonces el error se propaga, no se registra `TECHNICAL_FAILURE` y el registro se reentrega

Trazabilidad: `DeadLetterRecovererTest.should_rethrow_without_recording_when_dead_letter_topic_...`,
`DeadLetterRecovererTest.should_dead_letter_and_record_once_when_redelivered_after_dlt_failure`.

### REQ-OP-011 Clasificación de fallos de dependencias

Una respuesta `408`, `429`, `500`, `502`, `503` o `504`, un timeout, una conexión rechazada, un
fallo al obtener el token OAuth, un circuit breaker abierto o un bulkhead saturado DEBEN
clasificarse `EXTERNAL_TRANSIENT`. Cualquier otro `4xx` (salvo `404`) o un cuerpo ilegible o
incompleto DEBE clasificarse `EXTERNAL_PERMANENT`.

- **Escenario: 400 permanente**
  - Dado `PRD-014` configurado para responder `400` en `products-api`
  - Cuando un pedido PE de `CLI-30001` lo incluye
  - Entonces no hay reintentos y el pedido queda `TECHNICAL_FAILURE` con `EXTERNAL_PERMANENT`

Trazabilidad: `HttpAdaptersTest` (`should_retry_and_then_fail_transient_statuses`,
`should_fail_fast_on_permanent_statuses`, `should_treat_invalid_bodies_as_permanent`),
`LookupExchangeTest`, `resilience.feature`.

### REQ-OP-012 Reintentos por llamada

Cada llamada HTTP DEBE tener timeout de conexión `HTTP_CONNECT_TIMEOUT` (500 ms) y de respuesta
`HTTP_READ_TIMEOUT` (2 s), hasta `HTTP_RETRY_MAX_ATTEMPTS` (3) intentos sólo para
`EXTERNAL_TRANSIENT`, backoff exponencial 200 ms × 2 con jitter 0.5, respetando `Retry-After` hasta
2 s; circuit breaker por dependencia (ventana 20, mínimo 10, 50 %, 10 s abierto) y bulkhead de 32
llamadas con espera máxima de 500 ms.

- **Escenario: recuperación tras dos 503**
  - Dado `PRD-012` configurado para fallar 2 veces con `503`
  - Cuando un pedido de 20 × `PRD-012` a 120.0 se procesa
  - Entonces el pedido queda `APPROVED`

Trazabilidad: `ResilienceTest`, `RetryAfterParserTest`,
`HttpAdaptersTest.should_fail_fast_as_transient_when_circuit_is_open`, `resilience.feature`,
`OrderProcessingIT.should_approve_after_transient_failures_are_retried`.

### REQ-OP-013 Reintentos por registro

Sólo los fallos `EXTERNAL_TRANSIENT` y `PERSISTENCE` DEBEN reintentar el registro completo, hasta
`RECORD_RETRY_MAX_ATTEMPTS` (4) con backoff 1 s × 2. `DESERIALIZATION`, `VALIDATION`,
`VERSION_CONFLICT`, `EXTERNAL_PERMANENT` y `UNEXPECTED` NO DEBEN reintentarse.

- **Escenario: timeout reintentado**
  - Dado un producto que no responde a tiempo en el primer intento
  - Cuando el registro se reintenta
  - Entonces el pedido termina `APPROVED`

Trazabilidad: `ApplicationModelTest.should_classify_retryable_and_recordable_categories`,
`OrderCreatedListenerTest.should_mark_transient_failures_as_retryable_and_keep_command`,
`OrderProcessingIT.should_approve_after_a_timeout_is_retried`.

### REQ-OP-014 `TECHNICAL_FAILURE` explícito y recuperable

Al agotar los reintentos, o ante `EXTERNAL_PERMANENT`, `PERSISTENCE` o `UNEXPECTED` con comando
válido, el sistema DEBE publicar primero el mensaje en la DLT y luego registrar el pedido como
`TECHNICAL_FAILURE` (líneas sin precio, cliente sin resolver, totales en cero y `failure` con
`category`, `cause` saneada y `attempts`). NO DEBE publicar `orders.processed.v1` para ese estado ni
reemplazar un resultado terminal. El mismo evento reprocesado o una versión mayor DEBEN poder
resolverlo.

- **Escenario: cliente siempre caído**
  - Dado `CLI-40002` configurado para responder siempre `503`
  - Cuando se procesa un pedido MX con 5 × `PRD-001`
  - Entonces el pedido queda `TECHNICAL_FAILURE` con `failure.category=EXTERNAL_TRANSIENT`, la DLT
    recibe `x-error-category=EXTERNAL_TRANSIENT` y `orders.processed.v1` no recibe nada
- **Escenario: réplica tras corregir la causa**
  - Dado un pedido en `TECHNICAL_FAILURE`
  - Cuando se republica el mismo evento y la dependencia ya responde
  - Entonces el pedido pasa a `APPROVED`

Trazabilidad: `resilience.feature`, `DeadLetterRecovererTest`,
`OrderProcessingIT.should_record_technical_failure_and_recover_on_replay`,
`PersistenceIT.should_only_replace_technical_failure_of_the_same_event_at_equal_version`,
`VersionArbiterTest.should_allow_replay_of_same_event_after_technical_failure`.

### REQ-OP-015 Enriquecimiento en paralelo y acotado

El cliente y todos los productos DEBEN consultarse en paralelo sobre virtual threads, con un máximo
de `PROCESSING_MAX_CONCURRENT_LOOKUPS` (32) consultas simultáneas por instancia. Si una consulta
falla, las pendientes DEBEN cancelarse. Nada DEBE escribirse antes de la transacción final.

- **Escenario: una consulta falla**
  - Dado un pedido con tres productos y uno lanza `EXTERNAL_TRANSIENT`
  - Cuando se enriquece
  - Entonces las consultas pendientes se cancelan y el error se propaga sin persistir

Trazabilidad: `OrderEnricherTest` (`should_bound_concurrent_lookups`,
`should_cancel_pending_lookups_when_one_fails`),
`SystemAdaptersTest.should_run_lookups_on_virtual_threads`,
`ProcessOrderServiceTest.should_propagate_dependency_failures_without_persisting`.

### REQ-OP-016 Relay del outbox con lease y orden por pedido

Un relay DEBE reclamar cada `OUTBOX_RELAY_INTERVAL` (250 ms) hasta `OUTBOX_BATCH_SIZE` (100)
documentos `PENDING` disponibles o `IN_FLIGHT` con lease vencido, con un lease atómico de
`OUTBOX_LEASE` (30 s) y dueño `HOSTNAME`. NO DEBE reclamar un evento mientras exista una versión
anterior del mismo `orderId` sin publicar, ni dos eventos del mismo pedido en un lote. Publica con
productor idempotente (`acks=all`) y DEBE marcar `PUBLISHED` o liberar (con backoff 1 s … 60 s) sólo
si sigue siendo dueño del lease; el lote se asienta antes de `OUTBOX_SEND_TIMEOUT` (15 s).

- **Escenario: dos relays compitiendo**
  - Dado dos relays sobre el mismo outbox
  - Cuando ambos publican
  - Entonces cada evento se publica una vez
- **Escenario: instancia caída con lease tomado**
  - Dado documentos `IN_FLIGHT` de una instancia que murió
  - Cuando vence el lease
  - Entonces otra instancia los publica

Trazabilidad: `OutboxLeaseIT` (3 pruebas), `OutboxRelayIT`, `PublishPendingEventsServiceTest`,
`ApplicationModelTest.should_grow_relay_backoff_exponentially_up_to_cap`.

### REQ-OP-017 Consulta de un pedido

`GET /orders/{orderId}` DEBE devolver el pedido completo: identidad y versión, estado, mercado,
moneda, canal, snapshot del cliente (nombre descifrado), líneas con importes y tasas, totales,
`reason`, `violations`, `failure`, `occurredAt`, `receivedAt`, `processedAt`, `traceId` y
`taxRateEffectiveFrom`. Un `orderId` fuera de `^[A-Za-z0-9][A-Za-z0-9._:-]{0,63}$` DEBE responder
`400 VALIDATION_ERROR`; uno inexistente `404 ORDER_NOT_FOUND`; MongoDB caído `503`.

- **Escenario: pedido desconocido**
  - Dado el id `ORD-XX-404`
  - Cuando `analyst` lo consulta
  - Entonces responde `404` con `code=ORDER_NOT_FOUND` y `traceId`

Trazabilidad: `orders-api.feature`, `OrdersControllerTest`,
`OrdersApiIT.should_return_order_matching_openapi_contract_with_decrypted_name`.

### REQ-OP-018 Listado paginado de pedidos

`GET /orders` DEBE devolver resúmenes ordenados por `processedAt` descendente, con filtros
opcionales `status` (`APPROVED`, `REJECTED`, `TECHNICAL_FAILURE`) y `market` (dos letras), `page`
desde 0 y `size` de 1 a `API_MAX_PAGE_SIZE` (100, por defecto 20). `page × size` NO DEBE superar
`API_MAX_OFFSET` (10 000). Cualquier parámetro inválido DEBE responder `400` con todos los errores.

- **Escenario: filtros y paginación**
  - Dado un pedido MX aprobado
  - Cuando se pide `status=APPROVED&market=MX&page=0&size=5`
  - Entonces responde `page 0`, `size 5`, `totalElements`, `totalPages` e ítems sólo `APPROVED`/`MX`
- **Escenario: tamaño excesivo**
  - Dado `size=1000`
  - Cuando se lista
  - Entonces responde `400 VALIDATION_ERROR`

Trazabilidad: `orders-api.feature`, `OrdersControllerTest`
(`should_validate_every_listing_parameter`, `should_cap_deep_pagination`),
`OrdersApiIT.should_list_orders_matching_openapi_contract`.

### REQ-OP-019 Retención de inbox y outbox

Los documentos de `inbox` DEBEN expirar a los `INBOX_RETENTION` (30 d) de `receivedAt` y los de
`outbox` publicados a los `OUTBOX_RETENTION` (7 d) de `publishedAt`. Un cambio de retención DEBE
aplicarse al arrancar sobre el índice existente (`collMod`).

- **Escenario: cambio de retención**
  - Dado un índice TTL existente con otra duración
  - Cuando el servicio arranca
  - Entonces el índice se modifica en su lugar

Trazabilidad: `IndexInitializerTest`, `PersistenceIT.should_change_ttl_of_existing_index_in_place`.
