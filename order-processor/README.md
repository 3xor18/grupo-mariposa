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

## Configuración

Todo parámetro ajustable vive en `application.yml` como placeholder `${VARIABLE:default}` y se enlaza a
records `@ConfigurationProperties` validados al arrancar (un valor inválido detiene el arranque).
Opcionalmente se puede centralizar con Spring Cloud Config: si `CONFIG_SERVER_URL` tiene valor se importa
`configserver:<url>`; si está vacío el cliente queda desactivado y el servicio arranca sólo con el entorno.
Las propiedades son inmutables: un cambio en el config server se aplica con un reinicio progresivo.

### Secretos (sólo entorno, sin default en `application.yml`)

| Variable | Uso |
|---|---|
| `PII_ENCRYPTION_KEY` | **obligatoria**. AES-256 en base64 (32 bytes) |
| `PII_PREVIOUS_ENCRYPTION_KEY` | opcional, llave anterior durante una rotación (`PII_PREVIOUS_KEY_ID`) |
| `OAUTH_CLIENT_SECRET` | obligatoria si `OAUTH_ENABLED=true` |
| `SPRING_DATA_REDIS_PASSWORD` | opcional, contraseña de Redis |
| `DD_API_KEY` | sólo si `DATADOG_ENABLED=true` |
| credenciales de MongoDB | dentro de `MONGODB_URI` |

### Reglas de negocio (sin tocar el dominio)

| Variable | Default | Uso |
|---|---|---|
| `PLATFORM_MARKETS` | `MX:MXN:es-MX,CO:COP:es-CO,PE:PEN:es-PE,CL:CLP:es-CL,EC:USD:es-EC` | catálogo `mercado:moneda:locale` |
| `PLATFORM_CURRENCIES` | `MXN:2,COP:2,PEN:2,CLP:0,USD:2` | decimales por moneda (ISO 4217) |
| `PRICING_TAX_<MKT>_{STANDARD,REDUCED,EXEMPT}` | MX 0.16/0.08/0.00, CO 0.19/0.05/0.00, PE 0.18/0.10/0.00, CL 0.19/0.19/0.00, EC 0.15/0.05/0.00 | tabla de impuestos (0..1, completa por mercado del catálogo) |
| `PRICING_WHOLESALE_DISCOUNT_RATE` / `PRICING_WHOLESALE_DISCOUNT_MIN_QUANTITY` | `0.03` / `20` | descuento mayorista |

### Mercados y monedas

El mercado es un código ISO 3166 de dos letras (`MarketCode`) validado contra el catálogo de plataforma
(ADR [0006](../docs/adr/0006-configurable-market-catalog.md)): cada mercado declara su moneda y locale, y cada
moneda sus decimales. Un catálogo mal escrito, una moneda sin decimales o un mercado sin tasas detienen el
arranque. Los importes se redondean `HALF_UP` a los decimales de la moneda (CLP sin decimales, USD compartido
por EC). Un pedido de un mercado fuera del catálogo (p. ej. `AR`) o con otra moneda es un error de contrato y va
a `orders.processing.dlt` con categoría `VALIDATION`. Agregar un país es sólo configuración: sumarlo a
`PLATFORM_MARKETS` (y su moneda a `PLATFORM_CURRENCIES` si es nueva) y definir `PRICING_TAX_<MKT>_*`.

### Infraestructura y operación

| Variable | Default local | Uso |
|---|---|---|
| `CONFIG_SERVER_URL`, `CONFIG_SERVER_FAIL_FAST`, `CONFIG_SERVER_LABEL` | vacío, `false`, `main` | configuración centralizada |
| `MONGODB_URI` | `mongodb://localhost:27017/orders?replicaSet=rs0` | MongoDB (replica set obligatorio) |
| `MONGO_POOL_MAX_SIZE` / `MONGO_POOL_MIN_SIZE` / `MONGO_POOL_MAX_WAIT` / `MONGO_POOL_MAX_IDLE` | `50` / `0` / `2s` / `5m` | pool de conexiones |
| `MONGO_TX_ATTEMPTS` / `MONGO_TX_RETRY_BACKOFF` | `5` / `20ms` | reintentos de `TransientTransactionError` |
| `INBOX_RETENTION` / `OUTBOX_RETENTION` | `30d` / `7d` | TTL de inbox y outbox publicados |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka |
| `KAFKA_TOPIC_ORDERS_CREATED` / `_ORDERS_PROCESSED` / `KAFKA_TOPIC_DLT` (+ `_PARTITIONS`) | tópicos de la plataforma | nombres y particiones |
| `KAFKA_CONSUMER_GROUP` / `KAFKA_LISTENER_CONCURRENCY` / `KAFKA_MAX_POLL_RECORDS` | `order-processor` / `3` / `10` | consumo |
| `KAFKA_MAX_POLL_INTERVAL_MS`, `KAFKA_SESSION_TIMEOUT_MS`, `KAFKA_DELIVERY_TIMEOUT_MS`, `KAFKA_REQUEST_TIMEOUT_MS`, `KAFKA_LINGER_MS` | `300000`, `45000`, `10000`, `5000`, `5` | clientes Kafka |
| `KAFKA_CREATE_TOPICS` / `KAFKA_REPLICATION_FACTOR` | `false` / `3` | creación de tópicos (local/docker: `true` / `1` desde el config-repo) |
| `RECORD_RETRY_INITIAL_INTERVAL` / `RECORD_RETRY_MULTIPLIER` / `RECORD_RETRY_MAX_ATTEMPTS` | `1s` / `2.0` / `4` | reintento a nivel registro |
| `CLIENTS_API_URL` / `PRODUCTS_API_URL` | `http://localhost:8082` / `:8081` | dependencias HTTP |
| `HTTP_CONNECT_TIMEOUT` / `HTTP_READ_TIMEOUT` | `500ms` / `2s` | timeouts HTTP |
| `HTTP_RETRY_MAX_ATTEMPTS` / `HTTP_RETRY_INITIAL_BACKOFF` / `HTTP_RETRY_MULTIPLIER` / `HTTP_RETRY_JITTER` / `HTTP_RETRY_MAX_RETRY_AFTER` | `3` / `200ms` / `2.0` / `0.5` / `2s` | reintento por llamada |
| `HTTP_CB_SLIDING_WINDOW` / `HTTP_CB_MINIMUM_CALLS` / `HTTP_CB_FAILURE_RATE` / `HTTP_CB_OPEN_DURATION` / `HTTP_CB_HALF_OPEN_CALLS` | `20` / `10` / `50` / `10s` / `3` | circuit breaker por dependencia |
| `HTTP_BULKHEAD_MAX_CALLS` / `HTTP_BULKHEAD_MAX_WAIT` | `32` / `500ms` | bulkhead por dependencia |
| `PROCESSING_MAX_CONCURRENT_LOOKUPS` | `32` | fan-out máximo por instancia (no puede superar el bulkhead) |
| `OAUTH_ENABLED`, `OAUTH_TOKEN_URI`, `OAUTH_CLIENT_ID`, `OAUTH_REGISTRATION_ID` | `true`, Keycloak local, `order-processor` | client credentials |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_TIMEOUT` / `REDIS_CONNECT_TIMEOUT` | `localhost` / `6379` / `250ms` / `250ms` | Redis |
| `REDIS_POOL_ENABLED` / `REDIS_POOL_MAX_ACTIVE` / `REDIS_POOL_MAX_IDLE` / `REDIS_POOL_MIN_IDLE` / `REDIS_POOL_MAX_WAIT` | `false` / `16` / `8` / `0` / `250ms` | pool Lettuce (por defecto conexión multiplexada) |
| `CACHE_CLIENTS_ENABLED` / `CACHE_CLIENTS_TTL` / `CACHE_CLIENTS_KEY_PREFIX` | `true` / `60s` / `clients` | caché de clientes (`CACHE_ENABLED` es el default de ambas) |
| `CACHE_PRODUCTS_ENABLED` / `CACHE_PRODUCTS_TTL` / `CACHE_PRODUCTS_KEY_PREFIX` | `true` / `10m` / `products` | caché de productos |
| `KAFKA_MASTER_DATA_ENABLED` | `true` | arranca los listeners de cambios de datos maestros |
| `KAFKA_TOPIC_CLIENTS_CHANGED` / `KAFKA_TOPIC_PRODUCTS_CHANGED` / `KAFKA_TOPIC_MASTER_DATA_PARTITIONS` | `clients.changed.v1` / `products.changed.v1` / `3` | tópicos compactados de cambios (particiones sólo si `KAFKA_CREATE_TOPICS=true`) |
| `KAFKA_CACHE_CONSUMER_GROUP` / `KAFKA_MASTER_DATA_CONCURRENCY` | `order-processor-cache` / `1` | consumo de cambios |
| `OUTBOX_RELAY_ENABLED` / `OUTBOX_RELAY_INTERVAL` / `OUTBOX_BATCH_SIZE` / `OUTBOX_LEASE` / `OUTBOX_SEND_TIMEOUT` | `true` / `250ms` / `100` / `30s` / `15s` | relay |
| `OUTBOX_RETRY_INITIAL_BACKOFF` / `OUTBOX_RETRY_MAX_BACKOFF` | `1s` / `60s` | reintento de publicación |
| `API_DEFAULT_PAGE_SIZE` / `API_MAX_PAGE_SIZE` / `API_MAX_OFFSET` | `20` / `100` / `10000` | paginación de `GET /orders` (`page * size` acotado) |
| `API_DOCS_ENABLED` | `false` | publica Swagger UI y `/v3/api-docs` |
| `VALIDATION_PRODUCT_ID_PATTERN` / `VALIDATION_CLIENT_ID_PATTERN` | `^PRD-[A-Z0-9]{1,20}$` / `^CLI-[A-Z0-9]{1,20}$` | identificadores del contrato |
| `VALIDATION_PRICE_MAX_PRECISION` / `VALIDATION_PRICE_MAX_SCALE` / `VALIDATION_PRICE_MAX_VALUE` | `18` / `4` / `1000000000000` | límites de `unitPrice`; la API devuelve `unitPrice` tal como llegó (hasta 4 decimales), los importes con los decimales de la moneda |
| `AUTH_ENABLED`, `AUTH_REALM_URL`, `AUTH_ISSUER`, `AUTH_JWKS_URL`, `AUTH_AUDIENCE`, `AUTH_READER_ROLE`, `AUTH_ADMIN_ROLE` | `true`, realm `mariposa` local, derivados del realm, `${spring.application.name}`, `orders-reader`, `orders-admin` | JWT de Keycloak. `AUTH_ENABLED=false` sólo con el perfil `local`; con autenticación activa el servicio no arranca sin audiencia |
| `ALLOWED_ORIGINS` | `http://localhost:8090` | CORS del order-tracker |
| `PII_KEY_ID` / `PII_PREVIOUS_KEY_ID` | `k1` / vacío | identificador de llave (prefijo del texto cifrado) |
| `OTEL_EXPORTER_OTLP_ENDPOINT`, `TRACING_SAMPLING_PROBABILITY`, `TRACING_EXPORT_ENABLED` | `http://localhost:4318/v1/traces`, `0.1`, `true` | trazas OTLP |
| `LOG_FORMAT` / `LOG_LEVEL` | `ecs` / `INFO` | logs JSON estructurados |
| `DATADOG_ENABLED` | `false` | exportador opcional de métricas |
| `SERVER_PORT` / `SHUTDOWN_TIMEOUT` | `8080` / `20s` | servidor y apagado controlado |

Al arrancar se validan los presupuestos operativos y el servicio no inicia si no se cumplen:
fan-out ≤ bulkhead; `max.poll.records × presupuesto por intento + backoff de registro <
max.poll.interval.ms`; `delivery.timeout.ms ≤ OUTBOX_SEND_TIMEOUT < OUTBOX_LEASE`.

## Caché de datos maestros

Clientes y productos se cachean en Redis con la versión de la entidad (ADR
[0007](../docs/adr/0007-master-data-change-events-and-cache.md)):

- Claves `clients:{clientId}` y `products:{market}:{productId}`, un hash con `v` (versión) y `d` (perfil en
  JSON; el nombre del cliente va cifrado con la misma llave PII). La versión viene del campo `version` de la API
  o de su `ETag`; si no llega se usa `0`. Sólo se cachean resultados encontrados.
- Toda escritura pasa por un script Lua atómico ("set if newer version"): la escribe el relleno tras un miss y
  la aplica el listener de cambios. Una versión menor nunca pisa a una mayor; una lectura de la API más vieja que
  la caché no la sobrescribe (se devuelve la entrada más nueva) y un evento viejo se descarta.
- Listeners de `clients.changed.v1` y `products.changed.v1` (key `market:productId`) en el grupo
  `order-processor-cache`, con su propia container factory y `CommonLoggingErrorHandler`. Un evento completo
  sobrescribe la entrada; uno sin estado completo la borra dejando la versión como tope. Un evento mal formado
  se registra, cuenta como `ignored` y se salta: nunca bloquea la partición ni afecta el procesamiento de pedidos.
- El TTL (`CACHE_CLIENTS_TTL` 60 s, `CACHE_PRODUCTS_TTL` 10 min) es la red de seguridad si se pierde un evento.
  Si Redis falla, la lectura va directo a la API y el pedido sigue.
- Métricas: `orders_cache_hits_total`, `orders_cache_misses_total`, `orders_cache_errors_total{cache,operation}`
  y `orders_cache_invalidations_total{cache,outcome=applied|stale|ignored|error}`.

## Seguridad y datos personales

- `GET /orders/**` exige `orders-reader` u `orders-admin`; `/actuator/**` exige `orders-admin`, salvo
  health y `/actuator/prometheus`, que quedan sin autenticación para el scraping dentro de la red interna
  (no deben exponerse fuera del cluster). Swagger/OpenAPI sólo se publican con `API_DOCS_ENABLED=true`.
- El nombre del cliente se guarda cifrado (AES-256-GCM) y se devuelve descifrado a `orders-reader`:
  es un requisito de negocio del order-tracker. Nunca se loguea ni viaja en `orders.processed.v1`.
- Los errores inesperados se registran con tipo y mensaje sanitizado (sin secretos, tokens ni correos).

## Índices MongoDB

`orders`: `{status, processedAt}` y `{market, processedAt}` para `GET /orders`; `{processedAt}` para el
listado sin filtros; `{client.clientId}` para búsquedas operativas de soporte por cliente (consultas ad hoc,
todavía no expuestas en la API). `inbox` y `outbox` publicados tienen TTL (`INBOX_RETENTION`,
`OUTBOX_RETENTION`); un cambio de retención se aplica con `collMod` al arrancar. `outbox` además indexa
`{status, createdAt}` para el relay y `{orderId, eventVersion}` para el orden por pedido.

## Escalabilidad horizontal

Las instancias no guardan estado de negocio en memoria: la deduplicación, el orden por versión y el lease
del outbox viven en MongoDB, así que se pueden correr N réplicas del mismo grupo de consumidores. Lo único
local es cache de tokens OAuth, estado de circuit breaker y métricas, que no afectan la corrección. El
paralelismo escala con particiones × réplicas; los pools (MongoDB, Redis), el bulkhead y el fan-out se
ajustan por variable de entorno. `HOSTNAME` identifica al dueño del lease del outbox.

## Mapa de paquetes (`com.grupomariposa.orders`)

| Paquete | Contenido |
|---|---|
| `domain.model` | `Order`, `Money`, `Rate`, `MarketCode`, `MarketCatalog`, `CurrencyCatalog`, `TaxRateTable`, perfiles, `Decision`, `Violation` |
| `domain.policy` | `EligibilityPolicy`, `TaxPolicy`/`MarketTaxPolicy`, `DiscountPolicy`/`WholesaleVolumeDiscountPolicy`, `LinePricer` |
| `domain.service` | `OrderEvaluator` |
| `application.port.in` / `port.out` | casos de uso y puertos (`ClientDirectory`, `ProductCatalog`, `OrderStore`, `OutboxStore`, ...) |
| `application.validation` | `OrderCommandValidator` (contrato de entrada, devuelve todos los errores) |
| `application.service` | `ProcessOrderService`, `OrderEnricher` (fan-out en virtual threads), `VersionArbiter`, relay |
| `infrastructure.kafka` | listener, lector JSON estricto, DLT (headers + bytes originales), publicador del outbox, listeners de cambios de datos maestros |
| `infrastructure.http` | `RestClient` + Resilience4j, DTOs externos y mapeo de errores |
| `infrastructure.cache` | caché versionada en Redis (script Lua), decoradores de `ClientDirectory` y `ProductCatalog` |
| `infrastructure.masterdata` | `Versioned` y fuentes versionadas de clientes y productos |
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
  (`acks=all`) y marca `PUBLISHED` o libera **sólo si sigue siendo dueño del lease**
  (`leaseOwner` + `IN_FLIGHT`); un relay cuyo lease venció no puede pisar al que lo retomó. Cada lote se
  asienta antes de `OUTBOX_SEND_TIMEOUT`, que debe ser menor que el lease.
- Orden por pedido: no se reclama un evento mientras exista uno anterior (`eventVersion` menor) del mismo
  `orderId` sin publicar. Aun así la entrega es al menos una vez: los consumidores deduplican por `eventId` y
  descartan versiones menores a la última vista para el `orderId`.
