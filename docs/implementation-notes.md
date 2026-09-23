# Notas de implementación

Compara la propuesta inicial (`architecture-proposal.md`) con lo construido. Registra la evidencia nueva, la deuda
consciente y los riesgos que quedan.

## 1. Diferencias entre la propuesta y la implementación final

| Tema | Propuesta | Implementación | Motivo |
|---|---|---|---|
| Tasas de impuesto y descuento | tabla propiedad del enum `Market` | value objects `TaxRateTable`, `DiscountRule` y `MarketCurrencies` inyectados desde configuración y validados al arrancar | un cambio de ley no debe exigir recompilar; el dominio sigue sin depender de Spring |
| Configuración | variables de entorno por servicio | **Spring Cloud Config Server** + `config-repo/` versionado para los cuatro componentes (Java nativo; Go, NestJS y el contenedor de la PWA leen `/{app}-{perfil}.properties`) | un solo lugar auditable por PR; precedencia env > config server > default; en AWS el backend puede ser git o Parameter Store |
| Health checks | `/health/live` y `/health/ready` vía actuator | controlador propio para esos alias + `/livez` y `/readyz` | `additional-path` de actuator sólo admite un segmento |
| `traceparent` del evento de salida | el del procesamiento original | el del span del relay (observación de `KafkaTemplate`) | el relay publica en otro hilo y otra transacción; la correlación de negocio se mantiene por `orderId`/`eventId` |
| Headers de la DLT | todos los de Spring | se excluyen mensaje y stacktrace de la excepción; sólo quedan el nombre de la clase y la causa saneada | los mensajes de excepción pueden contener datos personales |
| `occurredAt` del evento de salida | no definido | igual a `processedAt` | es el momento en que ocurrió el hecho "pedido procesado" |
| Pedidos rechazados | no definido | las líneas se guardan sin precio y los totales en 0 | no se calcula dinero de un pedido que no se va a cumplir |
| `UNEXPECTED` | sólo DLT | también se registra como `TECHNICAL_FAILURE` | el contrato de la DLT lo declara reprocesable; así el pedido tiene un estado consultable |
| Cliente HTTP | HTTP/2 por defecto del JDK | forzado a HTTP/1.1 | el *upgrade* h2c producía envíos duplicados contra servidores HTTP/1.1 |
| Transacción con `DuplicateKey` | no contemplado | `setRollbackOnly()` explícito antes de clasificar | sin eso el commit falla con `NoSuchTransaction` etiquetado como transitorio y reintenta en bucle |
| Protección de fuerza bruta en Keycloak | valores por defecto | `failureFactor` 10, `quickLoginCheckMilliSeconds` 100 | las suites E2E ahora corren en serie y Karate pide un token por usuario para toda la corrida (`callSingle`), así que ya no hay ráfagas de logins del mismo usuario |

## 2. Evidencia nueva y restricciones encontradas

- El ejemplo del PDF (`ORD-MX-000147`) produce exactamente `grandTotal = 2100.11` con los datos semilla; se usa como
  prueba dorada en dominio, integración (Testcontainers), E2E (Karate) y UI (Playwright).
- Docker Engine 29 exige API ≥ 1.44: Testcontainers se fijó en 1.21.4.
- Flutter 3.44 ya no genera un service worker de caché; se agregó uno propio *network-first* que nunca cachea `/api`.
- La prueba de carga (`docs/load-test-results.md`) mostró que el cuello está en Mongo de un nodo y en el intervalo
  del relay, no en el worker: duplicar la concurrencia dio +20 %.

## 3. Deuda técnica consciente

| Deuda | Por qué se aceptó | Cómo se paga |
|---|---|---|
| Las métricas `outbox_pending`/`outbox_oldest_age` consultan Mongo en cada scrape | simple y exacto a este volumen | cachear el valor unos segundos o calcularlo en el relay |
| El relay publica los documentos de un lote en paralelo | throughput | ordenar por `orderId` dentro del lote si aparece un consumidor que lo requiera (hoy el consumidor descarta versiones menores) |
| Límites del contrato (largo de ids, 500 ítems, 256 caracteres de causa) son constantes | reflejan el JSON Schema, no son *tunables* | generarlos desde el esquema en build |
| El build de la imagen de `order-processor` no corre los tests | Testcontainers no puede correr dentro del build | se corren en CI antes del build |
| Configuración inmutable (records) | simplicidad y seguridad | un cambio en `config-repo` requiere reinicio progresivo; `@RefreshScope` sólo si se necesitara en caliente |
| Datos semilla en memoria en las APIs | lo permite el enunciado | implementar el puerto de repositorio con una base de datos; ni el dominio ni los consumidores cambian |
| Mercado como `enum` en los cuatro servicios y en los contratos | el enunciado fija MX, CO y PE *inicialmente*; el compilador impide procesar un mercado desconocido | catálogo de mercados configurable (TODO-1 de `roadmap.md`) antes del cuarto país |
| Tasas de impuesto en `config-repo` sin fecha de vigencia | cambio auditado por PR, validación completa al arrancar y tasa guardada en cada línea | colección `tax_rates` con `validFrom`/`validTo` y tasa elegida por `occurredAt` (TODO-2) |
| Clientes sin caché | una caché sólo por TTL podría aprobar pedidos de un cliente recién bloqueado | caché con invalidación por `clients.changed.v1` y TTL corto de respaldo (TODO-3) |

## 4. Funcionalidades no terminadas

- Schema Registry: los contratos se versionan como JSON Schema en el repo y se validan en CI y en tests.
- Reprocesamiento de la DLT desde una herramienta propia: hoy es manual (re-publicar el mensaje original, seguro por
  diseño). Un endpoint `POST /admin/dlt/replay` protegido con `orders-admin` sería el siguiente paso.
- Despliegue real en AWS: los artefactos (Helm, pipelines, OIDC, External Secrets) están listos pero no se aplicaron.

## 5. Riesgos residuales

- **Orden global**: sólo se garantiza por `orderId`. Si alguien consume `orders.processed.v1` asumiendo orden global, falla.
- **Caché de productos**: un producto descontinuado puede aprobarse hasta 5 minutos después (TTL configurable).
- **Rotación de la llave de PII**: soportada con `PII_KEY_ID`/`PII_PREVIOUS_ENCRYPTION_KEY`, pero falta el job que
  recifra documentos antiguos.
- **Config server como dependencia de arranque**: con `CONFIG_SERVER_FAIL_FAST=true` un config server caído impide
  arrancar nuevas réplicas (las que ya corren no se ven afectadas). En producción se despliega con 2 réplicas.

## 6. Siguiente cambio con más tiempo

1. Relay por Change Streams con *resume token* (menor latencia y sin polling), manteniendo el poller como respaldo.
2. Tests de contrato *consumer-driven* (Pact) entre `order-processor` y las dos APIs, publicados en un broker.
3. Autoescalado del worker por **lag de consumo** (KEDA) en lugar de CPU.
4. Endpoint de reproceso de la DLT con auditoría.
5. Mercados configurables, tasas con vigencia y caché de clientes invalidada por eventos: diseñados como TODO-1,
   TODO-2 y TODO-3 en [`roadmap.md`](roadmap.md), pensando en sumar más países de Latinoamérica.

## 7. Despliegue en EKS

- **Kafka (MSK)**: el worker usa el listener TLS (puerto 9094) con `SPRING_KAFKA_SECURITY_PROTOCOL=SSL` y no crea
  tópicos (`KAFKA_CREATE_TOPICS=false`, se aprovisionan con replicación 3). Siguiente paso: autenticación IAM
  (puerto 9098, `SASL_SSL` + `AWS_MSK_IAM` con `aws-msk-iam-auth` y el rol de IRSA ya declarado en
  `serviceAccount.roleArn`); requiere agregar la librería al servicio, por eso queda fuera de este cambio.
- **Redis (ElastiCache)**: TLS en tránsito con `SPRING_DATA_REDIS_SSL_ENABLED=true` y la contraseña (AUTH token) en
  `SPRING_DATA_REDIS_PASSWORD` desde Secrets Manager.
- **Réplicas del worker**: `orders.created.v1` tiene 6 particiones y cada pod abre 3 consumidores
  (`KAFKA_LISTENER_CONCURRENCY=3`), así que el HPA escala entre 1 y 2 réplicas: un tercer pod sólo tendría
  consumidores ociosos. El PDB usa `maxUnavailable: 1` para no bloquear el drenado de nodos con una réplica. Para escalar más hay que subir particiones y `maxReplicas` juntos, idealmente con KEDA por lag.
- **Arranque y apagado**: `startupProbe` por servicio (el worker tolera hasta 150 s de arranque) y `preStop` con
  `sleep` nativo de Kubernetes para que el Service deje de enviar tráfico antes del SIGTERM; el período de gracia
  cubre `preStop` + drenaje + apagado de cada servicio.
- **config-server**: 2 réplicas (3 en producción), backend git sobre este repositorio. `/actuator/prometheus` exige
  las mismas credenciales básicas que la configuración. En local, Prometheus las recibe por entorno y su
  entrypoint las escribe en archivos privados de `/tmp` (`basic_auth.username_file` / `password_file`), así que
  no quedan en el repositorio ni en `prometheus.yml`; en EKS el scrape usa un Secret con esas credenciales desde
  el namespace de monitoreo.
- **NetworkPolicy**: cada servicio acepta tráfico sólo de sus consumidores y del namespace de monitoreo, en el puerto
  `http`. El ALB (target type `ip`) llega desde la VPC, por eso `order-tracker` activa su política sólo en los
  overlays de ambiente, junto con el CIDR de la VPC (placeholder `10.0.0.0/16`); el chart falla si un servicio
  con Ingress activa la política sin `allowedCidrs`.
- **Keycloak**: el realm de `infra/keycloak` es sólo para local. Los usuarios demo y el cliente `orders-cli` no se
  despliegan; la rotación de refresh tokens (`revokeRefreshToken`) y las URIs exactas del `order-tracker` sí son
  las mismas que se esperan en el realm de cada ambiente.
