# ADR 0008 — Tasas de impuesto con vigencia y aprobación de cuatro ojos

- **Estado**: aceptado (cierra TODO-2 de `docs/roadmap.md`)
- **Fecha**: 2026-09-23

## Contexto
Las tasas vivían en `config-repo` (`PRICING_TAX_*`). Un cambio era un PR auditado, pero exigía un reinicio
progresivo, no admitía fecha de vigencia y no lo podía operar alguien de negocio. Los cambios de IVA se publican con
fecha: un pedido emitido antes de esa fecha debe pagar la tasa vieja aunque se procese después.

## Alternativas
1. Seguir en `config-repo` y coordinar el despliegue con la fecha del cambio: frágil y sin historial por período.
2. Un servicio de *pricing* nuevo con su base y su API: correcto a largo plazo, pero otro servicio que operar
   para un solo caso de uso.
3. Colección en MongoDB con invalidación por evento (`pricing.tax-rates.changed.v1`): cambios en milisegundos,
   pero con un tópico, un productor y un consumidor más.
4. **Colección `tax_rates` propiedad de `order-processor`, tabla completa en memoria de cada pod refrescada por
   intervalo, API de administración con aprobación de otra persona y `config-repo` como semilla y respaldo.**

## Decisión
Opción 4.
- **Modelo**: cada documento es un período `{ market, category, rate, validFrom, validTo, status, proposedBy,
  approvedBy | rejectedBy, changeReason, version }`. `validTo` es exclusivo y `null` deja la tasa abierta. Estados
  `PROPOSED → APPROVED | REJECTED`. Índice único parcial `{market, category, validFrom}` sobre los aprobados.
- **La tasa se elige por `occurredAt`** del pedido (o `receivedAt` si el evento no lo trae), no por la hora de
  procesamiento. El pedido guarda la tasa de cada línea y `taxRateEffectiveFrom`, el inicio del período más reciente
  aplicado a su mercado, para auditar con qué tabla se calculó.
- **Cuatro ojos**: `POST /tax-rates` crea una propuesta (quien propone sale del token, `preferred_username`) y
  `POST /tax-rates/{id}/approve` la aprueba otra persona; si es la misma responde `403 FOUR_EYES_REQUIRED`.
  Todo `/tax-rates` exige `orders-admin`. `validFrom` debe ser futuro (`TAX_RATES_ALLOW_PAST_VALID_FROM=false`).
- **Continuidad**: al aprobar, el período abierto anterior se cierra en `validFrom` del nuevo. Un solape o un hueco
  en la cobertura responde `409 TAX_RATE_CONFLICT`. La aprobación corre en una transacción que incrementa un
  documento guardia por `market:category` (`tax_rate_guards`), así dos aprobaciones concurrentes de la misma clave
  se serializan y una falla con conflicto en lugar de dejar períodos solapados.
- **Semilla**: al arrancar, cada combinación mercado/categoría sin períodos aprobados se copia desde `PRICING_TAX_*`
  con `validFrom = TAX_RATES_SEED_FROM` y actor `system-seed`. Es idempotente entre pods.
- **Lectura**: cada pod mantiene la tabla completa (`TaxRateSchedule`) en memoria y la recarga cada
  `TAX_RATES_REFRESH_INTERVAL` (30 s); el pod que aprueba la recarga de inmediato. Una carga que falla o que no
  cubre todos los mercados y categorías desde la semilla se descarta y se conserva la anterior. Si nunca se pudo
  cargar, se usan las tasas de configuración. Métricas: `orders_tax_rates_fallback` (1 mientras se usa la
  configuración) y `orders_tax_rates_refresh_failures_total`.

## Consecuencias
- Un cambio de tasa programado ya no requiere despliegue ni reinicio, y queda auditado quién lo propuso, quién lo
  aprobó y por qué.
- Los pods pueden ver una tabla distinta durante un intervalo de refresco. Como `validFrom` debe ser futuro, basta
  con aprobar con más anticipación que el intervalo; aprobar a segundos del inicio queda como riesgo aceptado
  (TODO-6 del roadmap).
- `config-repo` deja de ser la fuente de verdad de las tasas: sólo siembra combinaciones nuevas y actúa de
  respaldo. Cambiar `PRICING_TAX_*` no modifica un período ya sembrado.
- No hay borrado de períodos: un error se corrige con otra propuesta aprobada, así el historial queda completo.

## Cuándo revisarla
Si el negocio exige que el cambio se vea en todos los pods en milisegundos, o si otro servicio necesita las tasas,
se publica `pricing.tax-rates.changed.v1` desde un outbox y la colección pasa a un servicio de *pricing* propio sin
cambiar el dominio (mismo puerto `TaxRateSource`).
