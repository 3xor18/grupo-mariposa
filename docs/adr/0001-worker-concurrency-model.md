# ADR 0001 — Modelo de concurrencia y procesamiento del worker

- **Estado**: aceptado
- **Fecha**: 2026-09-22

## Contexto
`order-processor` consume `orders.created.v1` (6 particiones, key `orderId`). Por cada pedido hace
1 llamada a `clients-api` y N a `products-api` (I/O bound, timeout de 2 s), calcula en memoria y escribe
en MongoDB. Debe tolerar duplicados, reordenamientos y varias instancias a la vez.

## Alternativas
1. **Listener tradicional con pool de hilos de plataforma**: simple, pero el fan-out HTTP bloquea hilos caros y
   limita el paralelismo interno de cada pedido.
2. **WebFlux / Reactor Kafka**: máximo throughput, pero obliga a un modelo reactivo de punta a punta
   (Mongo reactivo, transacciones reactivas), sube la complejidad cognitiva y el costo de onboarding.
3. **Listener por partición + virtual threads para el fan-out** (Java 21).

## Decisión
Opción 3. Spring Kafka con `concurrency` = particiones asignadas por instancia, procesamiento
**registro a registro** y confirmación manual con `AckMode.RECORD`. Dentro del registro, cliente y productos se
consultan en paralelo en un `Executor` de virtual threads, acotado por un bulkhead (32 llamadas) para no
saturar las APIs. `spring.threads.virtual.enabled=true` para el servidor web. La corrección ante carreras no
depende de hilos ni de locks de aplicación: la dan las restricciones atómicas de MongoDB (ADR 0002).

## Consecuencias
- Código imperativo, legible y fácil de testear. La latencia por pedido ≈ la llamada más lenta, no la suma.
- El orden por `orderId` se conserva por partición. El paralelismo se escala agregando particiones e instancias.
- Riesgo de *pinning* de virtual threads en bloques `synchronized` (Java 21): se evita `synchronized`
  en el camino caliente y los clientes HTTP usan el `HttpClient` del JDK.
- Throughput máximo ≈ particiones × (1 / latencia por pedido). Con 6 particiones y ~50 ms ≈ 120 pedidos/s por
  grupo. Más carga ⇒ más particiones.

## Cuándo revisarla
- Si el p99 por pedido supera 1 s de forma sostenida o se necesitan > 500 pedidos/s: evaluar procesamiento
  por lotes o paralelismo por key dentro de la partición (Confluent Parallel Consumer).
- Al migrar a Java 24+ (sin pinning por `synchronized`), revisar el tamaño del bulkhead.
