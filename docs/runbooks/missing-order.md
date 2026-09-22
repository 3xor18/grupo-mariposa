# Runbook — "Envié el pedido y no aparece ni aprobado ni rechazado"

Objetivo: ubicar en qué tramo de la cadena se quedó el pedido en menos de 15 minutos, **sin suposiciones**.
La búsqueda va de lo más barato a lo más caro y cada paso descarta un tramo.

```
Productor → orders.created.v1 → order-processor → Mongo (orders/inbox/outbox) → relay → orders.processed.v1
                                      ↘ orders.processing.dlt
```

## 0. Datos que hay que pedir
`orderId` (obligatorio), fecha y hora aproximadas, mercado, `eventId` si el productor lo tiene.

## 1. ¿Está en MongoDB? (fuente de verdad del resultado)
```js
db.orders.findOne({ _id: "<orderId>" }, { status: 1, eventVersion: 1, sourceEventId: 1, failure: 1, processedAt: 1 })
db.inbox.find({ orderId: "<orderId>" }).sort({ receivedAt: -1 })
db.outbox.find({ orderId: "<orderId>" }, { status: 1, attempts: 1, createdAt: 1, publishedAt: 1 })
```
| Resultado | Diagnóstico | Acción |
|---|---|---|
| `status = TECHNICAL_FAILURE` | una dependencia falló y se agotaron los reintentos | ver `failure`, corregir la causa, reprocesar desde la DLT (paso 4) |
| `APPROVED/REJECTED` y outbox `PENDING` | el relay está atrasado o no puede publicar | paso 5 |
| `APPROVED/REJECTED` y outbox `PUBLISHED` | se procesó y se publicó: el problema está en el consumidor aguas abajo | entregar `eventId` de salida y `publishedAt` al equipo consumidor |
| inbox con resultado `STALE` o `DUPLICATE` | llegó una versión vieja o repetida | confirmar con el productor qué versión envió |
| no existe | nunca llegó a persistirse | paso 2 |

## 2. ¿Llegó a Kafka?
- Logs de `order-processor` filtrados por `orderId` (en Grafana/Loki o Datadog: `@orderId:<orderId>`). La primera
  transición es `RECEIVED`. Si existe, el `traceId` lleva a la traza completa en Jaeger/Datadog APM.
- Si no hay logs: buscar el mensaje en el tópico (Kafka UI → `orders.created.v1` → buscar por key `orderId`).
  - **No está en el tópico** → el problema es del productor (no publicó, publicó en otro tópico o con otra key).
  - **Está en el tópico pero sin logs** → paso 3.

## 3. ¿El consumidor está atrasado o detenido?
- Métrica de lag del consumer group `order-processor` por partición (`kafka_consumergroup_lag`).
  Lag creciente en **una** partición = mensaje venenoso o bloqueo en esa partición.
- `orders_processing_latency` p99 y `resilience4j_circuitbreaker_state`: un circuit breaker abierto frena el
  procesamiento con reintentos a nivel de registro.
- Pods reiniciándose (`kubectl get pods`, `OOMKilled`) o rebalanceos continuos en los logs.

## 4. ¿Está en la DLT?
Kafka UI → `orders.processing.dlt` → buscar por key o header `x-order-id`.
| `x-error-category` | Significado | Acción |
|---|---|---|
| `VALIDATION` / `DESERIALIZATION` | el mensaje viola el contrato | el productor debe publicar un evento corregido |
| `VERSION_CONFLICT` | otro evento distinto ya ocupó esa versión del pedido | escalar al productor: está reutilizando versiones |
| `EXTERNAL_TRANSIENT` / `PERSISTENCE` | la dependencia o Mongo no respondieron | una vez resuelta la causa, **reinyectar** el mensaje tal cual en `orders.created.v1` (es seguro: inbox + regla de `TECHNICAL_FAILURE`) |
| `EXTERNAL_PERMANENT` | una API respondió 4xx inesperado | revisar credenciales o cambios de contrato del proveedor |

## 5. Outbox atrasado
- `outbox_pending` y la antigüedad del pendiente más viejo. Si crecen: revisar los logs del relay (`PUBLISH_FAILED`),
  la conectividad con Kafka y los ACL del tópico de salida.
- Los documentos `IN_FLIGHT` con `leaseUntil` vencido se retoman solos. Si no, hay que revisar que el scheduler esté
  corriendo en al menos una instancia.

## 6. Cierre
Registrar en el incidente: tramo donde se detuvo, causa raíz, acción tomada y si hace falta una alerta nueva para
detectarlo antes (por ejemplo, alerta de lag por partición o de antigüedad del outbox).
