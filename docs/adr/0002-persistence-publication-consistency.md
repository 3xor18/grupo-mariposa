# ADR 0002 — Consistencia entre persistencia y publicación

- **Estado**: aceptado
- **Fecha**: 2026-09-22

## Contexto
Cada pedido procesado debe quedar en MongoDB y anunciarse en `orders.processed.v1`. Son dos sistemas sin
transacción distribuida. Los estados prohibidos son: pedido sin evento, evento sin pedido y más de un
efecto de negocio para el mismo pedido.

## Alternativas
1. **Guardar y luego publicar** en el mismo hilo: una caída entre los dos pasos pierde el evento.
2. **Publicar y luego guardar**: evento sin pedido si falla la escritura.
3. **Transacciones de Kafka**: son atómicas sólo dentro de Kafka; MongoDB queda fuera.
4. **Change Data Capture (Debezium)**: robusto, pero agrega un conector y una plataforma más que operar.
5. **Transactional Outbox + Inbox en MongoDB** con relay propio.

## Decisión
Opción 5. En **una transacción** (replica set) se escriben:
- `inbox` (`_id = eventId`): deduplicación de entrada.
- `orders` (`_id = orderId`): upsert condicional por `eventVersion` (ver architecture-proposal §6).
- `outbox`: el evento de salida con su `eventId` ya generado.

Un relay reclama documentos `PENDING` con lease atómico, publica con productor idempotente
(`acks=all`, `enable.idempotence=true`) y los marca `PUBLISHED`. El offset de entrada se confirma sólo después
del commit en Mongo.

## Consecuencias
- Pedido sin evento: no puede pasar de forma permanente (el outbox se reintenta hasta publicar).
- Evento sin pedido: imposible (el evento nace del outbox ya confirmado).
- Duplicados de salida: posibles (at-least-once), pero con el **mismo** `eventId`, así que el consumidor deduplica.
- Latencia extra de publicación ≈ intervalo del relay (250 ms). Hay que monitorear `outbox.pending` y su antigüedad.
- Se requiere replica set incluso en local.

## Cuándo revisarla
- Si la latencia de publicación importa por debajo de 100 ms o el volumen supera ~1 000 eventos/s: migrar
  el relay a Change Streams o a Debezium.
- Si aparece un consumidor que necesita orden global estricto (hoy sólo se garantiza orden por `orderId`).
