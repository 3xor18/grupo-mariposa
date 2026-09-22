# order-processor

Worker Java 21 / Spring Boot 3.5 que consume `orders.created.v1`, valida, enriquece con `clients-api` y
`products-api`, decide `APPROVED` / `REJECTED`, persiste en MongoDB y publica `orders.processed.v1` mediante
un outbox transaccional. Expone la lectura en `GET /orders/{orderId}` y `GET /orders`.
Diseño: [`docs/architecture-proposal.md`](../docs/architecture-proposal.md) y [`docs/adr`](../docs/adr).

## Ejecutar

```bash
docker build -t order-processor .
docker run -p 8080:8080 \
  -e MONGODB_URI='mongodb://mongo:27017/orders?replicaSet=rs0' \
  -e KAFKA_BOOTSTRAP_SERVERS=kafka:9092 -e REDIS_HOST=redis \
  -e PII_ENCRYPTION_KEY="$(openssl rand -base64 32)" -e OAUTH_CLIENT_SECRET=... \
  order-processor
```

Sin Docker: `./mvnw spring-boot:run` con las mismas variables (MongoDB debe ser replica set).
Swagger UI en `/swagger-ui.html`; salud en `/health/live` y `/health/ready`; métricas en
`/actuator/prometheus`.

## Probar

```bash
./mvnw verify
```

Corre Checkstyle, tests unitarios (Surefire), integración con Testcontainers (`*IT`, Failsafe: Kafka,
MongoDB replica set, Redis y WireMock embebido), JaCoCo (100 % de líneas en `domain` y `application`,
≥ 90 % global sobre unitarios + integración) y SpotBugs. Requiere Docker para los `*IT`.

## Variables de entorno

| Variable | Default local | Uso |
|---|---|---|
| `MONGODB_URI` | `mongodb://localhost:27017/orders?replicaSet=rs0` | MongoDB (replica set obligatorio) |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka |
| `KAFKA_LISTENER_CONCURRENCY` | `3` | consumidores por instancia |
| `KAFKA_CREATE_TOPICS` | `true` | crea los tópicos si no existen |
| `RECORD_RETRY_INITIAL_INTERVAL` / `RECORD_RETRY_MAX_ATTEMPTS` | `1s` / `4` | reintento a nivel registro |
| `CLIENTS_API_URL` / `PRODUCTS_API_URL` | `http://localhost:8082` / `:8081` | dependencias HTTP |
| `HTTP_CONNECT_TIMEOUT` / `HTTP_READ_TIMEOUT` | `500ms` / `2s` | timeouts HTTP |
| `OAUTH_ENABLED`, `OAUTH_TOKEN_URI`, `OAUTH_CLIENT_ID`, `OAUTH_CLIENT_SECRET` | `true`, Keycloak local, `order-processor`, vacío | client credentials hacia las APIs |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` | `localhost` / `6379` / vacío | caché de productos |
| `CACHE_ENABLED` / `CACHE_PRODUCTS_TTL` | `true` / `5m` | caché de productos |
| `PII_ENCRYPTION_KEY` | **obligatoria** | AES-256 en base64 (32 bytes) |
| `PII_KEY_ID`, `PII_PREVIOUS_ENCRYPTION_KEY`, `PII_PREVIOUS_KEY_ID` | `k1`, vacío, vacío | rotación de llaves |
| `AUTH_ENABLED`, `AUTH_ISSUER`, `AUTH_JWKS_URL` | `true`, realm `mariposa` local | JWT de Keycloak |
| `ALLOWED_ORIGINS` | `http://localhost:8090` | CORS del order-tracker |
| `OUTBOX_RELAY_ENABLED` / `OUTBOX_RELAY_INTERVAL` / `OUTBOX_BATCH_SIZE` | `true` / `250ms` / `100` | relay del outbox |
| `OTEL_EXPORTER_OTLP_ENDPOINT`, `TRACING_SAMPLING_PROBABILITY`, `TRACING_EXPORT_ENABLED` | `http://localhost:4318/v1/traces`, `1.0`, `true` | trazas OTLP |
| `LOG_FORMAT` | `ecs` | logs JSON estructurados (`ecs`, `logstash`, `gelf`) |
| `DATADOG_ENABLED` / `DD_API_KEY` | `false` / vacío | exportador opcional de métricas |

Ningún secreto vive en `application.yml`: sólo placeholders que se resuelven desde el entorno.

## Mapa de paquetes (`com.grupomariposa.orders`)

| Paquete | Contenido |
|---|---|
| `domain.model` | `Order`, `Money`, `Rate`, `Market` (tabla de impuestos), perfiles, `Decision`, `Violation` |
| `domain.policy` | `EligibilityPolicy`, `TaxPolicy`/`MarketTaxPolicy`, `DiscountPolicy`/`WholesaleVolumeDiscountPolicy`, `LinePricer` |
| `domain.service` | `OrderEvaluator` |
| `application.port.in` / `port.out` | casos de uso y puertos (`ClientDirectory`, `ProductCatalog`, `OrderStore`, `OutboxStore`, ...) |
| `application.validation` | `OrderCommandValidator` (contrato de entrada, devuelve todos los errores) |
| `application.service` | `ProcessOrderService`, `OrderEnricher` (fan-out en virtual threads), `VersionArbiter`, relay |
| `infrastructure.kafka` | listener, lector JSON estricto, DLT (headers + bytes originales), publicador del outbox |
| `infrastructure.http` | `RestClient` + Resilience4j, DTOs externos y mapeo de errores |
| `infrastructure.cache` | decorador Redis de `ProductCatalog` |
| `infrastructure.persistence` | documentos Mongo, transacción inbox + orders + outbox, índices |
| `infrastructure.crypto` | AES-256-GCM para el nombre del cliente |
| `infrastructure.web` | API de lectura, ProblemDetail, seguridad JWT |
| `infrastructure.observability` | métricas, logs con MDC, health de Kafka |
| `infrastructure.config` | cableado de beans y propiedades de procesamiento |

Las reglas de dependencias (dominio puro, aplicación sólo depende del dominio, sin ciclos) están
verificadas con ArchUnit en `ArchitectureTest`.

## Idempotencia, concurrencia y outbox

- **Inbox** (`_id = eventId`) + **upsert condicional** de `orders` (`_id = orderId`, gana la versión mayor;
  la misma versión sólo se reescribe si es el mismo evento en `TECHNICAL_FAILURE`) + **outbox**, todo en una
  transacción MongoDB. Un `DuplicateKey` se clasifica en duplicado, obsoleto o conflicto de versión.
  Ver ADR [0001](../docs/adr/0001-worker-concurrency-model.md) y
  [0002](../docs/adr/0002-persistence-publication-consistency.md).
- El offset se confirma registro a registro (`AckMode.RECORD`) sólo después del commit en Mongo o de la
  publicación en `orders.processing.dlt`.
- El relay reclama lotes con lease atómico (`findAndModify`), publica con productor idempotente
  (`acks=all`) y marca `PUBLISHED`; una instancia caída libera su lease al vencer.
