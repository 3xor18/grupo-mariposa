# Eventos y contratos (EVT)

## Propósito

Los componentes no comparten código, sólo contratos versionados en `contracts/`: JSON Schema de
eventos Kafka, OpenAPI de las APIs y un error común RFC 9457. Esta spec fija su forma, su semántica
de entrega y las reglas para evolucionarlos sin romper a nadie.

## Alcance

- `orders.created.v1`, `orders.processed.v1`, `orders.processing.dlt`, `clients.changed.v1`,
  `products.changed.v1`, OpenAPI de las tres APIs, `problem.schema.json`, tópicos y claves,
  compatibilidad y verificación en CI y en tests.

## Fuera de alcance

- Reglas de negocio que producen cada evento (`order-processing.md`, `clients.md`, `products.md`).

## Requisitos

### REQ-EVT-001 Contratos como fuente de verdad

Todo contrato DEBE vivir en `contracts/` (JSON Schema 2020-12 en `events/`, OpenAPI 3.1 en `http/`,
error común en `common/`), con dueño declarado en `.github/CODEOWNERS`. Cada servicio DEBE validar
sus respuestas y eventos reales contra esos archivos en sus propias pruebas.

- **Escenario: validación en tests**
  - Dado una respuesta real de `GET /orders/{id}`
  - Cuando corre la prueba de contrato
  - Entonces se valida contra el esquema `Order` de `order-processor.openapi.yaml` sin errores

Trazabilidad: `order-processor/.../support/Contracts.java`, `OrdersApiIT`,
`products-api/internal/httpapi/contract_test.go`, `clients-api/test/contract.e2e-spec.ts`.

### REQ-EVT-002 `orders.created.v1`

El evento de entrada DEBE requerir `eventId`, `orderId`, `market`, `currency`, `clientId` e `items`
(`productId`, `quantity`, `unitPrice`), con `eventVersion` (por defecto 1), `occurredAt` y `channel`
opcionales; key = `orderId`; dueño: equipo de intake. El consumidor DEBE ignorar campos
desconocidos.

- **Escenario: ejemplo del contrato**
  - Dado `contracts/examples/orders.created.v1.approved.json` (`ORD-MX-000147`)
  - Cuando `order-processor` lo lee
  - Entonces produce el comando esperado; un campo extra no cambia el resultado

Trazabilidad: `OrderMessageReaderTest` (`should_read_contract_example`,
`should_ignore_unknown_fields`), `scripts/ci/check-contracts.sh` (ajv sobre ejemplos y muestras).

### REQ-EVT-003 `orders.processed.v1`

Por cada decisión `APPROVED` o `REJECTED` DEBE publicarse un evento con key `orderId`, headers
`eventType=OrderProcessed` y `eventId`, y cuerpo: `eventId` (UUIDv7 generado una vez al guardar),
`eventVersion`, `occurredAt` (= `processedAt`), `sourceEventId`, `orderId`, `clientId` (sin nombre),
`status`, `market`, `currency`, `totals`, `reason` (null si aprobado) y `violations`. NO DEBE
publicarse para `TECHNICAL_FAILURE`.

- **Escenario: aprobación dorada**
  - Dado `ORD-MX-000147` aprobado
  - Cuando se publica
  - Entonces el evento valida contra el esquema, `status=APPROVED`, `reason=null`,
    `totals.grandTotal=2100.11`
- **Escenario: rechazo**
  - Dado un pedido con varias violaciones
  - Cuando se publica
  - Entonces `reason` es la primera y `violations` las lista todas en orden

Trazabilidad: `OutboxPayloadFactoryTest`, `OutboundKafkaTest`,
`OrderProcessingIT.should_approve_golden_order_and_publish_contract_compliant_event`,
`order-processing.feature`, `resilience.feature` (sin evento en `TECHNICAL_FAILURE`).

### REQ-EVT-004 Entrega al menos una vez con identidad estable

La publicación DEBE ser al menos una vez: un reenvío del mismo documento de outbox DEBE conservar su
`eventId`. Los consumidores DEBEN deduplicar por `eventId` y descartar versiones menores a la última
vista del `orderId`. El orden sólo está garantizado por `orderId`.

- **Escenario: entradas dejadas por una instancia caída**
  - Dado un documento `PENDING`, uno `IN_FLIGHT` con lease vencido hace 60 s y uno `IN_FLIGHT`
    con lease vigente 10 min
  - Cuando corre el relay
  - Entonces publica los dos primeros con su payload intacto y los marca `PUBLISHED`; el tercero
    sigue `IN_FLIGHT` y no se publica

Trazabilidad: `OutboxRelayIT.should_publish_entries_left_behind_by_a_crashed_instance`,
`OutboxLeaseIT.should_publish_versions_of_the_same_order_in_order`; deduplicación en consumidores:
⚠️ sin test (no hay consumidor en el repositorio).

### REQ-EVT-005 `orders.processing.dlt`

El valor DEBE ser el mensaje original byte a byte y la key la original. Headers obligatorios:
`x-error-category`, `x-error-cause` (≤ 256 caracteres, saneada), `x-attempts`, `x-failed-at`
(ISO-8601 UTC), `x-component=order-processor`; `x-order-id` y `x-event-id` cuando se pudieron
extraer; más los `kafka_dlt-original-*` de Spring. NO DEBE incluir mensaje ni stacktrace de la
excepción ni el header interno de intento de entrega.

- **Escenario: producto repetido**
  - Dado `ORD-MX-E2EV…2` con `PRD-001` dos veces
  - Cuando llega a la DLT
  - Entonces lleva `VALIDATION`, `x-component=order-processor`, `x-order-id`, `x-event-id`,
    `x-attempts`, `x-failed-at`, `x-error-cause` y el valor es igual al evento publicado

Trazabilidad: `validation.feature`, `OrderDeadLetterPublisherTest`, `DltSupportTest`,
`contracts/events/orders.processing.dlt.md`.

### REQ-EVT-006 Reproceso desde la DLT

Los mensajes con `EXTERNAL_TRANSIENT`, `PERSISTENCE` y `UNEXPECTED` DEBEN poder republicarse sin
cambios en `orders.created.v1` una vez corregida la causa; `VALIDATION`, `DESERIALIZATION` y
`VERSION_CONFLICT` requieren un evento corregido del productor. El reproceso es manual.

- **Escenario: réplica segura**
  - Dado un pedido en `TECHNICAL_FAILURE` por una dependencia caída
  - Cuando se republica el mensaje original
  - Entonces se resuelve sin duplicar efectos

Trazabilidad: `OrderProcessingIT.should_record_technical_failure_and_recover_on_replay`,
`contracts/events/orders.processing.dlt.md`.

### REQ-EVT-007 Eventos de cambio de datos maestros

`clients.changed.v1` (key `clientId`) y `products.changed.v1` (key `market:productId`) DEBEN llevar
el estado completo de la entidad con `eventId`, `occurredAt` y `version` ≥ 1; `clients.changed.v1`
NO DEBE llevar `name`. Los consumidores DEBEN quedarse con la mayor `version` por clave.

- **Escenario: muestras**
  - Dado `samples/changes/clients.changed.v1.json` (`CLI-70001`, v2, `BLOCKED`) y
    `samples/changes/products.changed.v1.json` (`PRD-020`, MX, v2, `DISCONTINUED`)
  - Cuando CI las valida con ajv
  - Entonces cumplen sus esquemas

Trazabilidad: `scripts/ci/check-contracts.sh`, `client-changed.event.spec.ts`,
`catalog/service_test.go` (`TestChangeEventMatchesContract`), `master-data-cache.feature` (claves
`CLI-70001` y `MX:PRD-020`).

### REQ-EVT-008 Tópicos, particiones y claves

Los tópicos DEBEN ser: `orders.created.v1` (6 particiones, key `orderId`), `orders.processed.v1`
(6, `orderId`), `orders.processing.dlt` (3, `orderId`), `clients.changed.v1` (3, compactado,
`clientId`) y `products.changed.v1` (3, compactado, `market:productId`). En local `kafka-init` los
crea con retención de 7 días para los de pedidos y 30 días para la DLT. En EKS se aprovisionan con
replicación 3 y `order-processor` no los crea (`KAFKA_CREATE_TOPICS=false`).

- **Escenario: creación local**
  - Dado Compose con `kafka-init`
  - Cuando arranca la plataforma
  - Entonces existen los cinco tópicos con esas particiones

Trazabilidad: ⚠️ sin test (claves verificadas indirectamente por Karate con `kafka.readAfter` por
key; particiones y compactación sin prueba).

### REQ-EVT-009 Error común RFC 9457

Todo error HTTP DEBE ser `application/problem+json` con `type` (URI base + código en kebab-case),
`title`, `status`, `code`, `detail`, `instance`, `traceId` y `timestamp`; los `400` de validación
DEBEN incluir `errors[]` con `field` y `message`.

- **Escenario: pedido inexistente**
  - Dado `GET /orders/ORD-XX-404`
  - Cuando responde
  - Entonces cumple `problem.schema.json` con `status 404` y `code=ORDER_NOT_FOUND`

Trazabilidad: `OrdersApiIT.should_answer_problem_details_for_errors`,
`contract_test.go` (`TestContractProblemResponses`, `TestProblemCatalogIsComplete`),
`clients-api/test/contract.e2e-spec.ts`, `e2e/karate/.../common/problem.json`.

### REQ-EVT-010 Evolución compatible

Dentro de una versión sólo DEBEN hacerse cambios aditivos (campo opcional, valor de enum documentado
como abierto). Un cambio incompatible DEBE publicarse como tópico `.v2` o ruta `/v2`, con
publicación dual (*expand → migrate → contract*). En cada PR, CI DEBE comparar los OpenAPI contra la
rama base con `oasdiff breaking --fail-on ERR` (con excepciones aceptadas en
`contracts/oasdiff/err-ignore.txt`), compilar todos los JSON Schema y validar ejemplos y muestras.

- **Escenario: quitar un campo de respuesta**
  - Dado un PR que elimina `totals` de `Order`
  - Cuando corre el job `contracts`
  - Entonces oasdiff marca un cambio `ERR` y el job falla

Trazabilidad: `scripts/ci/check-contracts.sh`, `.github/workflows/ci.yml` (job `contracts`),
`Jenkinsfile` (stages `contracts schemas` / `contracts breaking changes`).
