# Prueba de carga — resultados y análisis

Entorno: laptop Windows 11, Docker Desktop (16 CPU lógicas, 16 GB), toda la plataforma en un solo host con
Kafka de 1 broker y MongoDB replica set de 1 nodo. Los números sirven para comparar configuraciones, no como
capacidad productiva.

## 1. Procesamiento de eventos (`load/event-burst.sh`)

Ráfaga de 2 000 pedidos únicos más 200 duplicados exactos (1 de cada 10), publicados en `orders.created.v1`.
Cada pedido llama a `clients-api`, a `products-api` (dos productos) y escribe inbox + orders + outbox en una
transacción.

| Configuración | Mensajes | Pedidos únicos | Tiempo total | Throughput | Aprobados | Duplicados detectados |
|---|---|---|---|---|---|---|
| 1 instancia, concurrencia 3 | 2 200 | 2 000 | 31 s | 64 pedidos/s | 2 000 | 200 |
| 1 instancia, concurrencia 6 (= particiones) | 2 200 | 2 000 | 26 s | 76 pedidos/s | 2 000 | 200 |
| Idem, tras la revisión (relay con lotes acotados por deadline, fan-out ≤ bulkhead) | 2 200 | 2 000 | 14 s | 142 pedidos/s | 2 000 | 200 |

- **Correctitud bajo carga**: 0 efectos duplicados, 0 fallos técnicos, 0 mensajes en la DLT; el outbox publicó
  exactamente 2 000 eventos y quedó en 0 pendientes segundos después de terminar la ráfaga.
- **Latencia media por pedido**: ~32 ms (Micrometer `orders_processing_latency_seconds`).
- El cambio de concurrencia se hizo **sólo en `config-repo`** (`KAFKA_LISTENER_CONCURRENCY`) y un reinicio, sin recompilar.

### Dónde está el cuello de botella
La ganancia de 3 → 6 hilos fue de ~20 %, no 2×. El límite no está en la CPU del worker sino en:
1. MongoDB de un solo nodo con transacciones (`writeConcern: majority` en un RS de 1 miembro sigue sincronizando el journal).
2. El outbox relay publica al ritmo del intervalo (250 ms × 100 documentos); durante la ráfaga llegó a acumular
   ~1 100 pendientes que drenó en pocos segundos. Subir `OUTBOX_BATCH_SIZE` o bajar `OUTBOX_RELAY_INTERVAL` reduce
   ese lag a costa de más consultas a Mongo.
3. Todo corre en el mismo host (Kafka, Mongo, las APIs y el worker compiten por disco y CPU).

### Cómo escalaría en producción
- Más particiones en `orders.created.v1` y más réplicas del worker (HPA por lag de consumo, no sólo por CPU).
- MongoDB Atlas con 3 nodos y escritura en el primario; índices ya creados por el servicio.
- Relay en modo "drain": si un lote vuelve lleno, repetir sin esperar el intervalo.

## 2. APIs HTTP (`load/k6/apis.js`)

Tasa constante durante 60 s: 100 iteraciones/s de catálogo (1 producto + 1 cliente) y 20 iteraciones/s de
`GET /orders` paginado, todo con JWT real de Keycloak.

| Métrica | Resultado | Umbral |
|---|---|---|
| Requests | 13 203 | — |
| Errores | 0,00 % | < 1 % ✅ |
| Catálogo p95 | 2,52 ms → 1,75 ms tras la revisión | < 50 ms ✅ |
| Consulta de pedidos p95 | 15,96 ms → 8,44 ms tras la revisión | < 150 ms ✅ |
| Checks | 100 % (13 202 / 13 202) | — |

Ejecución:

```bash
./load/event-burst.sh 2000 10 300
docker run --rm --network grupo-mariposa_default -v "$PWD/load/k6:/scripts" \
  -e KEYCLOAK_URL=http://keycloak:8080 -e PRODUCTS_URL=http://products-api:8081 \
  -e CLIENTS_URL=http://clients-api:3000 -e ORDERS_URL=http://order-processor:8080 \
  -e DEMO_PASSWORD=<DEMO_USER_PASSWORD> grafana/k6:0.54.0 run /scripts/apis.js
```

El token se obtiene una sola vez en `setup()` y se comparte entre los usuarios virtuales: 30 logins simultáneos
del mismo usuario activan la protección de fuerza bruta de Keycloak (`user_temporarily_disabled`), que es el
comportamiento correcto del IdP, no un fallo de la plataforma.
