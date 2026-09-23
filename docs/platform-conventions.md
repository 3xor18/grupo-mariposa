# Convenciones de plataforma (estándar compartido)

Lo que está aquí es **transversal**: todos los servicios lo cumplen. Lo que no aparece queda a criterio de
cada equipo.

## Puertos locales

| Servicio | Puerto interno | Puerto host |
|---|---|---|
| order-processor | 8080 | 8080 |
| products-api | 8081 | 8081 |
| clients-api | 3000 | 8082 |
| order-tracker (nginx + PWA) | 8080 | 8090 |
| Keycloak | 8080 | 8180 |
| Kafka (interno / externo) | 9092 / 19092 | 19092 |
| Kafka UI | 8080 | 8085 |
| MongoDB (replica set `rs0`) | 27017 | 27017 |
| Redis | 6379 | — (sólo red interna) |
| Prometheus | 9090 | 9090 |
| Grafana | 3000 | 3001 |
| Jaeger UI / OTLP gRPC / OTLP HTTP | 16686 / 4317 / 4318 | 16686 |
| config-server | 8888 | 8888 |

Todos los puertos publicados en el host se enlazan a `127.0.0.1`: nada de la plataforma local queda expuesto a
la red del equipo.

## Tópicos Kafka

| Tópico | Particiones | Key | Dueño |
|---|---|---|---|
| `orders.created.v1` | 6 | `orderId` | intake de pedidos |
| `orders.processed.v1` | 6 | `orderId` | order-processor |
| `orders.processing.dlt` | 3 | `orderId` | order-processor |
| `clients.changed.v1` | 3, compactado | `clientId` | clients-api |
| `products.changed.v1` | 3, compactado | `market:productId` | products-api |

## Catálogo de mercados (ADR 0006)

Compartido por los cuatro servicios desde `config-repo/application.yml`. Un mercado fuera del catálogo es `VALIDATION`.

| Clave config server | Variable | Valor |
|---|---|---|
| `platform.markets` | `PLATFORM_MARKETS` | `MX:MXN:es-MX,CO:COP:es-CO,PE:PEN:es-PE,CL:CLP:es-CL,EC:USD:es-EC` |
| `platform.currencies` | `PLATFORM_CURRENCIES` | `MXN:2,COP:2,PEN:2,CLP:0,USD:2` (decimales ISO 4217) |

- Formato de mercado `CODIGO:MONEDA:LOCALE`; código ISO 3166-1 alfa-2, moneda ISO 4217. Varias monedas pueden
  repetirse entre mercados (EC usa USD). Todos los importes se redondean HALF_UP a los decimales de la moneda.
- Impuestos (sólo `order-processor`): tabla completa STANDARD/REDUCED/EXEMPT por cada mercado del catálogo.
  CL 0.19/0.19/0.00 (Chile no tiene IVA reducido), EC 0.15/0.05/0.00.

## Datos maestros, eventos de cambio y caché (ADR 0007)

| Servicio | Base MongoDB | Usuario | Escritura | Rol |
|---|---|---|---|---|
| clients-api | `clients` (`clients`, `outbox`) | `${MONGO_CLIENTS_USERNAME}` | `PATCH /clients/{clientId}` | `clients-admin` |
| products-api | `products` (`products`, `outbox`) | `${MONGO_PRODUCTS_USERNAME}` | `PATCH /products/{productId}?market=` | `products-admin` |

- Concurrencia optimista: cada entidad tiene `version` (entero, empieza en 1). `GET` responde `ETag: "<version>"`;
  `PATCH` acepta `If-Match` y responde `412 PRECONDITION_FAILED` si no coincide.
- El cambio y el evento se guardan en **la misma transacción** (outbox). Un relay con lease publica en el tópico
  del servicio. Semilla: se inserta al arrancar sólo si el documento no existe (upsert `$setOnInsert`).
- Variables de las APIs: `MONGODB_URI` (secreto), `KAFKA_BOOTSTRAP_SERVERS`, `KAFKA_TLS_ENABLED` (`false` en
  Compose, `true` en EKS contra MSK), `SEED_ENABLED` / `seed.enabled` (carga la semilla si falta; `true` sólo en el
  perfil `docker`, `false` en staging y producción), `OUTBOX_RELAY_INTERVAL_MS` (250), `OUTBOX_BATCH_SIZE` (100),
  `OUTBOX_LEASE_MS` (30000), `OUTBOX_RETRY_DELAY_MS` (1000, espera antes de reintentar una publicación fallida),
  `OUTBOX_RETENTION` (tiempo que se conservan los eventos ya publicados antes de purgarlos) y `KAFKA_TOPIC_CHANGES`
  (`clients.changed.v1` / `products.changed.v1`).
- `order-processor` cachea en Redis `clients:{clientId}` y `products:{market}:{productId}` con la `version`. Un
  listener (grupo `order-processor-cache`) aplica cada evento sólo si su versión es mayor (script atómico en Redis);
  las lecturas desde la API tampoco pisan una versión mayor. TTL de respaldo: `CACHE_CLIENTS_TTL` 60s,
  `CACHE_PRODUCTS_TTL` 10m.

## HTTP

- Errores: `application/problem+json` según `contracts/common/problem.schema.json` (ADR 0004).
- Health: `GET /health/live` y `GET /health/ready` → `{"status":"UP"}` (sin autenticación).
- Métricas Prometheus: Go `GET /metrics`, NestJS `GET /metrics`, Spring `GET /actuator/prometheus`.
- Correlación: se acepta y propaga `traceparent` (W3C) y `X-Request-Id`; si no llega, se genera. Todo log y todo
  `Problem` incluye el `traceId`.
- Logs JSON a stdout con `timestamp`, `level`, `service`, `traceId`, `message` y, cuando existen, `orderId`/`eventId`.
  Nunca se loguean tokens, secretos ni nombres de clientes.

## Seguridad (ADR 0003)

| Variable | Ejemplo | Uso |
|---|---|---|
| `AUTH_ENABLED` | `true` | desactivable sólo para tests locales |
| `AUTH_ISSUER` | `http://localhost:8180/realms/mariposa` | claim `iss` esperado |
| `AUTH_JWKS_URL` | `http://keycloak:8080/realms/mariposa/protocol/openid-connect/certs` | llaves públicas (red interna) |
| `AUTH_REQUIRED_ROLE` | `products-reader` / `clients-reader` | rol en `realm_access.roles` |
| `AUTH_AUDIENCE` | `products-api` / `clients-api` / `order-processor` | claim `aud` exigido |

Respuesta sin token o con token inválido → `401 UNAUTHORIZED`. Token válido sin el rol → `403 FORBIDDEN`.

## Inyección de fallos (para demostrar resiliencia)

Variable `FAULT_RULES` en `products-api` y `clients-api`: lista separada por comas de `id:tipo[:veces]`. Sólo
se aplica con `FAULT_INJECTION_ENABLED=true`, que únicamente activa el perfil `docker` del `config-repo`;
staging y producción la fijan en `false` (y `clients-api` la rechaza con `NODE_ENV=production`).

- `tipo`: `429`, `500`, `502`, `503`, `400`, `timeout`.
- `veces`: la regla falla las primeras N solicitudes de ese id y después responde normal. Sin N falla siempre.
- `timeout` retiene la respuesta `FAULT_TIMEOUT_MS` ms (por defecto 5000) o hasta que el cliente cancele.
- Ejemplo: `FAULT_RULES=PRD-012:503:2,PRD-013:timeout,PRD-014:400`.

## Rate limiting
Token bucket por proceso: `RATE_LIMIT_RPS` (por defecto 200) y `RATE_LIMIT_BURST` (por defecto 400). Al excederse
responde `429 RATE_LIMITED` con el header `Retry-After` en segundos.

## Datos semilla

### Productos (disponibilidad por mercado)

| productId | Mercado | Nombre | SKU | Estado | Categoría |
|---|---|---|---|---|---|
| PRD-001 | MX, CO, PE | Bebida 600 ml | BEB-600-PET | ACTIVE | STANDARD |
| PRD-002 | MX | Agua natural 1 L | AGU-1000-PET | ACTIVE | EXEMPT |
| PRD-003 | MX | Galletas surtidas 200 g | GAL-200-SUR | ACTIVE | REDUCED |
| PRD-004 | MX | Refresco retornable 355 ml | REF-355-VID | DISCONTINUED | STANDARD |
| PRD-008 | MX | Jugo de naranja 1 L | JUG-1000-NAR | ACTIVE | STANDARD |
| PRD-012 | MX | Café molido 500 g | CAF-500-MOL | ACTIVE | REDUCED |
| PRD-013 | MX | Té verde 20 sobres | TEV-020-SOB | ACTIVE | REDUCED |
| PRD-005 | CO | Arepa precocida 1 kg | ARE-1000-PRE | ACTIVE | REDUCED |
| PRD-006 | CO | Café tostado 250 g | CAF-250-TOS | ACTIVE | STANDARD |
| PRD-007 | CO | Panela 500 g | PAN-500-BLQ | DISCONTINUED | EXEMPT |
| PRD-009 | PE | Quinua 1 kg | QUI-1000-BLA | ACTIVE | EXEMPT |
| PRD-010 | PE | Chocolate 90 g | CHO-090-BAR | ACTIVE | REDUCED |
| PRD-011 | PE | Galleta de soda 6 un | GAL-006-SOD | ACTIVE | STANDARD |
| PRD-014 | PE | Aceite vegetal 1 L | ACE-1000-VEG | ACTIVE | STANDARD |
| PRD-001 | CL, EC | Bebida 600 ml | BEB-600-PET | ACTIVE | STANDARD |
| PRD-015 | CL | Vino tinto 750 ml | VIN-750-TIN | ACTIVE | STANDARD |
| PRD-016 | CL | Harina de trigo 1 kg | HAR-1000-TRI | ACTIVE | REDUCED |
| PRD-017 | CL | Pan de molde 500 g | PAN-500-MOL | DISCONTINUED | STANDARD |
| PRD-018 | EC | Banano 1 kg | BAN-1000-CAV | ACTIVE | EXEMPT |
| PRD-019 | EC | Cemento 50 kg | CEM-050-GRI | ACTIVE | REDUCED |
| PRD-020 | MX | Producto demo caché | DEM-001-CCH | ACTIVE | STANDARD |

### Clientes

| clientId | Nombre | Mercado | Segmento | Régimen | Estado |
|---|---|---|---|---|---|
| CLI-99821 | Distribuidora Central | MX | WHOLESALE | GENERAL | ACTIVE |
| CLI-10002 | Abarrotes La Esperanza | MX | RETAIL | SIMPLIFIED | ACTIVE |
| CLI-10003 | Comercializadora del Norte | MX | WHOLESALE | EXEMPT | ACTIVE |
| CLI-20001 | Mayorista Andino | CO | WHOLESALE | GENERAL | ACTIVE |
| CLI-20002 | Tienda El Porvenir | CO | RETAIL | GENERAL | BLOCKED |
| CLI-30001 | Distribuidora Lima Norte | PE | WHOLESALE | GENERAL | ACTIVE |
| CLI-30002 | Bodega San Martín | PE | RETAIL | EXEMPT | ACTIVE |
| CLI-40001 | Distribuidora Resiliente | MX | WHOLESALE | GENERAL | ACTIVE |
| CLI-40002 | Comercial Intermitente | MX | RETAIL | GENERAL | ACTIVE |
| CLI-50001 | Distribuidora Santiago | CL | WHOLESALE | GENERAL | ACTIVE |
| CLI-50002 | Almacén Valparaíso | CL | RETAIL | GENERAL | ACTIVE |
| CLI-60001 | Mayorista Guayaquil | EC | WHOLESALE | GENERAL | ACTIVE |
| CLI-60002 | Tienda Quito | EC | RETAIL | EXEMPT | ACTIVE |
| CLI-70001 | Cliente demo caché | MX | WHOLESALE | GENERAL | ACTIVE |

`PRD-020` y `CLI-70001` son exclusivos para las pruebas de invalidación de caché (se bloquean y reactivan).

Reglas de fallo por defecto en Compose: `products-api` → `PRD-012:503:2,PRD-013:timeout,PRD-014:400`;
`clients-api` → `CLI-40001:503:2,CLI-40002:503`.
