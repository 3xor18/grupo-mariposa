# Roadmap técnico (TODO)

Mejoras identificadas y priorizadas para cuando la plataforma crezca. No bloquean la primera versión, pero cada una
tiene el problema que resuelve, el diseño propuesto, qué componentes toca y un criterio de terminado.

| ID | Mejora | Motivación | Prioridad |
|---|---|---|---|
| TODO-1 | Mercados configurables en lugar de enums | Se agregarán más países de Latinoamérica | Alta, antes del cuarto país |
| TODO-2 | Tasas de impuesto en una colección con vigencia | Cambios regulatorios con fecha de entrada en vigor | Alta, junto con TODO-1 |
| TODO-3 | Caché de clientes con invalidación por eventos | Reducir latencia y carga sobre `clients-api` | Media |

---

## TODO-1 — Mercados configurables

**Situación actual.** El enunciado pide soportar *inicialmente* MX, CO y PE. Hoy el mercado es una lista fija
(`enum`) en los cinco lugares donde se usa:

| Componente | Dónde |
|---|---|
| order-processor | `domain/model/Market.java` |
| products-api | `internal/product/product.go` (`MarketMX`, `MarketCO`, `MarketPE`) |
| clients-api | `src/clients/domain/market.enum.ts` |
| order-tracker | `lib/features/orders/domain/entities/market.dart` |
| contratos | enums `market`/`currency` en `contracts/events/*.json` y `contracts/http/*.yaml` |

Las tasas y la moneda de cada mercado ya están en `config-repo`, pero **agregar un país exige cambiar código en los
cuatro servicios y los contratos**. Un pedido con un mercado no soportado se rechaza como `VALIDATION`, va a la DLT y
no se persiste; nunca se procesa con un impuesto inventado.

**Diseño propuesto.**
1. Reemplazar el enum por un value object `MarketCode` (ISO 3166-1 alfa-2) que se valida contra un **catálogo de
   mercados** configurado: código, moneda ISO 4217 y locale de formato.
2. El catálogo vive en `config-repo` (`markets.yml`, compartido por los cuatro servicios vía `application.yml`) y en
   el futuro puede pasar a la colección de TODO-2 sin tocar el dominio (mismo puerto `MarketCatalog`).
3. Validación al arrancar: cada mercado del catálogo debe tener moneda, las tres categorías de impuesto y locale;
   si falta algo, el servicio no arranca.
4. Contratos: `market` y `currency` pasan de `enum` a `pattern` (`^[A-Z]{2}$` y `^[A-Z]{3}$`) con la lista vigente
   documentada. Es un cambio compatible para productores; se coordina con los consumidores con el flujo
   *expand → migrate → contract* de `docs/technical-leadership.md`.
5. La PWA recibe los mercados disponibles desde `config.json` (o un endpoint `GET /markets`), no de un enum.

**Criterio de terminado.** Agregar Chile (CL/CLP) es **sólo** un PR a `config-repo` y a los datos de productos y
clientes: sin cambios de código, con tests parametrizados que cargan un catálogo de cuatro mercados.

---

## TODO-2 — Tasas de impuesto en una colección con vigencia

**Situación actual.** Las tasas viven en `config-repo`. Ventajas: cambio por PR revisado y auditado, historial en
git, rollback inmediato, validación completa al arrancar, y cada línea del pedido guarda la tasa aplicada
(`taxRate`), así que el histórico no se pierde. Límites: requiere reinicio, no admite fecha de vigencia y un usuario
de negocio no puede editarlas.

**Diseño propuesto.**
1. Colección `tax_rates` (propiedad de un servicio de *pricing* o de `order-processor` hasta que exista):
   `{ market, category, rate, validFrom, validTo, changedBy, changeReason }` con índice único
   `{market, category, validFrom}` y validación de que los rangos no se solapen.
2. Puerto `TaxRateSource` en aplicación; el adaptador Mongo arma la `TaxRateTable` que ya usa el dominio, así que
   las reglas de negocio no cambian.
3. **La tasa se elige por la fecha del pedido** (`occurredAt`), no por la fecha de procesamiento: un pedido emitido
   el 31/12 con IVA viejo y procesado el 01/01 paga el IVA viejo. Supuesto a validar con el negocio.
4. Caché en memoria con TTL corto e invalidación por evento `pricing.tax-rates.changed.v1`.
5. API de administración protegida con `orders-admin` y aprobación en dos pasos (quien crea no aprueba).
6. `config-repo` queda como valor semilla y respaldo si la colección no responde al arrancar.

**Criterio de terminado.** Un cambio de tasa programado para una fecha futura se aplica solo a los pedidos con
`occurredAt` desde esa fecha, sin reinicios, con auditoría de quién y por qué.

---

## TODO-3 — Caché de clientes con invalidación por eventos

**Situación actual.** Los productos se cachean en Redis (TTL de 5 min); los clientes **no**. Un cliente puede pasar a
`BLOCKED` en cualquier momento, y con una caché sólo por tiempo podríamos aprobar pedidos de un cliente ya bloqueado
durante todo el TTL. Hoy el costo de no cachear es bajo: es una llamada por pedido, contra N llamadas de productos.

**Diseño propuesto.**
1. `clients-api` publica `clients.changed.v1` (key `clientId`, con outbox) cada vez que cambia estado, segmento,
   régimen o mercado de un cliente. Requiere que `clients-api` tenga persistencia real (hoy usa datos semilla).
2. `order-processor` agrega el decorador `CachingClientDirectory` (mismo patrón que `CachingProductCatalog`) y un
   listener de `clients.changed.v1` que **actualiza o borra** la entrada en Redis al instante.
3. TTL corto (por ejemplo 60 s) como red de seguridad si se pierde un evento, y versión del cliente en la entrada
   para descartar actualizaciones que lleguen fuera de orden.
4. La misma invalidación por eventos se aplica a productos (`products.changed.v1`) para bajar el TTL de 5 min a
   segundos y cerrar el riesgo de aprobar un producto recién descontinuado.
5. Métricas: `orders_cache_hits_total{cache="clients"}` y lag del listener de invalidación.

**Riesgo residual aceptado.** Queda una ventana igual a la latencia del evento (milisegundos a pocos segundos). Si
el negocio exige cero ventana para clientes bloqueados, se mantiene la consulta directa sólo para esa verificación.

**Criterio de terminado.** Bloquear un cliente en `clients-api` hace que el siguiente pedido de ese cliente sea
rechazado aunque su perfil estuviera en caché (test de integración con Kafka y Redis reales).
