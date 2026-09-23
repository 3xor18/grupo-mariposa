# Operación, entrega y despliegue (OPS)

## Propósito

Levantar la plataforma completa con un solo comando, operarla y demostrarla con `mariposa.sh`,
bloquear en CI todo cambio que rompa calidad, contratos o seguridad, y desplegar en EKS de forma
repetible y auditada.

## Alcance

- Docker Compose local, comandos de `mariposa.sh`, escenarios de ejemplo, pruebas de carga, gates de
  CI (GitHub Actions y Jenkins), chart de Helm, despliegue en EKS, apagado controlado y runbooks.

## Fuera de alcance

- Configuración por ambiente (`configuration.md`), alertas y dashboards (`observability.md`).

## Requisitos

### REQ-OPS-001 Arranque con un comando

`./mariposa.sh up` (o `.\mariposa.ps1 up`) DEBE: generar `.env` desde `.env.example` con secretos
aleatorios (32 caracteres; `PII_ENCRYPTION_KEY` de 32 bytes en base64) si no existe, o agregar las
claves nuevas sin tocar las existentes; construir y levantar Compose esperando los healthchecks
(`--wait`); e imprimir las URLs sin la contraseña demo (sólo con `urls --show-secrets`).

- **Escenario: `.env` de una versión anterior**
  - Dado un `.env` existente sin `MONGO_CLIENTS_PASSWORD`
  - Cuando se ejecuta `./mariposa.sh init`
  - Entonces se agrega la clave con un secreto aleatorio y las demás no cambian

Trazabilidad: ⚠️ sin test (`mariposa.sh`, `cmd_init`).

### REQ-OPS-002 Plataforma local

Compose DEBE levantar 15 contenedores: 13 de larga duración (`mongo` replica set `rs0`, `kafka`,
`kafka-ui`, `redis`, `keycloak`, `config-server`, `products-api`, `clients-api`, `order-processor`,
`order-tracker`, `prometheus`, `grafana`, `jaeger`) y dos pasos únicos (`kafka-init` crea tópicos,
`mongo-users` crea o sincroniza los usuarios de `orders`, `clients` y `products`); `datadog-agent`
es un perfil opcional. Todo puerto publicado DEBE enlazarse a `127.0.0.1`; Redis no se publica.

- **Escenario: plataforma en CI**
  - Dado el `.env` construido desde GitHub Secrets
  - Cuando el job `e2e` ejecuta `docker compose up -d --build --wait --wait-timeout 600`
  - Entonces todos los healthchecks pasan antes de correr Karate y Playwright

Trazabilidad: `.github/workflows/ci.yml` (job `e2e`), `docker-compose.yml`.

### REQ-OPS-003 Comandos de operación y demostración

`mariposa.sh` DEBE ofrecer: `init`, `up`, `down`, `clean` (borra volúmenes), `status`,
`logs [svc]`, `urls [--show-secrets]`, `publish <archivo>` (publica en `orders.created.v1` con key
`orderId`), `scenarios` (los 26 de `samples/events`), `consume [tópico]` (por defecto
`orders.processed.v1`, con key y headers), `token [usuario]` (por defecto `analyst`, vía
`orders-cli`), `block-client <id>` / `unblock-client <id>` (`PATCH` con `If-Match` como `admin`),
`mongo [expr]` (base `orders`), `test`, `e2e` y `demo-traffic [min]`. Un id de cliente fuera de
`^CLI-[A-Z0-9]{1,20}$` DEBE rechazarse antes de llamar a la API.

- **Escenario: demo de invalidación**
  - Dado la plataforma arriba
  - Cuando se ejecuta `./mariposa.sh block-client CLI-70001`
  - Entonces el siguiente pedido de `CLI-70001` se rechaza con `CLIENT_NOT_ACTIVE`

Trazabilidad: `mariposa.sh` e2e usado por CI (`./mariposa.sh e2e`); resto de comandos ⚠️ sin test.

### REQ-OPS-004 Escenarios de ejemplo

`samples/events/` DEBE contener 26 escenarios publicables (aprobados por mercado, exento, rechazos,
inválidos, transitorios que se recuperan, fallos técnicos, duplicado, conflicto, versión nueva y
obsoleta, Chile, Ecuador, `AR`), y los válidos DEBEN cumplir `orders.created.v1`.

- **Escenario: validación en CI**
  - Dado las muestras `01` a `09`, `12` a `20`, `22`, `23`, `24` y `26`
  - Cuando corre `check-contracts.sh`
  - Entonces validan contra el esquema; los inválidos (10, 11, 21, 25) se excluyen a propósito

Trazabilidad: `scripts/ci/check-contracts.sh` (`VALID_EVENTS`).

### REQ-OPS-005 Gates de CI

Todo PR a `develop`/`main` DEBE pasar, según las carpetas cambiadas: lint, pruebas y umbral de
cobertura por componente (`order-processor` `mvnw verify` con Checkstyle, SpotBugs, ArchUnit y
JaCoCo; `products-api` `golangci-lint`, `go test -race` y ≥ `GO_COVERAGE_MIN` 95 %; `clients-api`
lint y `test:cov`; `order-tracker` `dart format`, `flutter analyze`, 100 % de líneas;
`config-server` 100 %), contratos (oasdiff + ajv), Helm (`helm lint --strict` + kubeconform por
servicio y ambiente), compilación de Karate e imágenes con Trivy (`CRITICAL,HIGH` corregibles
fallan). El job `e2e` (Compose + Karate + Playwright) DEBE correr en push y en PR a `main`. Todas
las actions DEBEN fijarse por SHA.

- **Escenario: vulnerabilidad alta**
  - Dado una imagen con una CVE `HIGH` con parche disponible
  - Cuando corre el job `images`
  - Entonces falla y bloquea `e2e`

Trazabilidad: `.github/workflows/ci.yml`, `scripts/ci/check-go-coverage.sh`,
`scripts/ci/check-lcov-coverage.sh`, `scripts/ci/validate-helm.sh`, `Jenkinsfile` (stage
`Quality gates`).

### REQ-OPS-006 Despliegue controlado

El despliegue DEBE ser manual (`workflow_dispatch`) y sólo desde `main` o tags, con servicios
validados contra una lista permitida (`config-server`, `products-api`, `clients-api`,
`order-processor`, `order-tracker`), rol de AWS asumido por OIDC (sin llaves estáticas), escaneo
Trivy antes del push a ECR y `helm upgrade --install --atomic --wait` con `values/<servicio>.yaml` +
`values/<ambiente>/<servicio>.yaml`. Jenkins DEBE hacer lo mismo con `sts assume-role`.

- **Escenario: servicio desconocido**
  - Dado `services=order-processor,foo`
  - Cuando se resuelve la lista
  - Entonces falla con "Unknown service 'foo'"

Trazabilidad: ⚠️ sin test (`.github/workflows/deploy.yml`, `scripts/ci/resolve-services.sh`,
`scripts/ci/deploy-eks.sh`, `Jenkinsfile`; preparado, no aplicado).

### REQ-OPS-007 Chart único de Helm

Todos los servicios DEBEN desplegarse con `deploy/helm/mariposa-service`: Deployment con UID
numérico y raíz de sólo lectura, `startupProbe`, `preStop` con `sleep`, Service, HPA, PDB,
NetworkPolicy, ExternalSecret (Secrets Manager `grupo-mariposa/<servicio>`) e Ingress ALB sólo para
`order-tracker`. `order-processor` DEBE escalar entre 1 y 2 réplicas (6 particiones / 3 consumidores
por pod) con `maxUnavailable: 1`; `config-server` con 2 réplicas (3 en producción).

- **Escenario: render de todos los servicios**
  - Dado los values de staging y producción
  - Cuando corre `validate-helm.sh`
  - Entonces cada servicio pasa `helm lint --strict` y kubeconform para Kubernetes 1.33

Trazabilidad: `scripts/ci/validate-helm.sh`, `.github/workflows/ci.yml` (job `helm`).

### REQ-OPS-008 Apagado controlado

Cada servicio DEBE drenar antes de terminar: readiness `DOWN`, fin del trabajo en curso y cierre
ordenado (`order-processor` `SHUTDOWN_TIMEOUT` 20 s; `products-api` 3 s de drenaje + 10 s;
`clients-api` 5 s + 10 s), con el período de gracia de Kubernetes cubriendo `preStop` + drenaje.

- **Escenario: SIGTERM en products-api**
  - Dado solicitudes en curso
  - Cuando llega `SIGTERM`
  - Entonces readiness pasa a `DOWN`, sigue sirviendo el drenaje y luego cierra

Trazabilidad: `internal/app/app_test.go` (`TestGracefulShutdownDrainsWhileNotReady`),
`platform.e2e-spec.ts` (`should_drain_with_readiness_down_cancel_held_requests_and_close_...`),
`graceful-shutdown.spec.ts`.

### REQ-OPS-009 Pruebas de carga y suites de plataforma

El repositorio DEBE incluir una ráfaga de eventos (`load/event-burst.sh`), una prueba k6 de las APIs
(`load/k6/apis.js`) con resultados en `docs/load-test-results.md`, y `./mariposa.sh e2e` DEBE
publicar el pedido semilla de la UI (`samples/e2e/ui-golden-order.json`) y correr Karate y
Playwright contra la plataforma levantada.

- **Escenario: suite de aceptación**
  - Dado la plataforma arriba
  - Cuando se ejecuta `./mariposa.sh e2e`
  - Entonces corren las features de `e2e/karate` y `order-tracker/e2e`

Trazabilidad: `e2e/karate/src/test/resources/mariposa/e2e/*.feature`,
`order-tracker/e2e/tests/smoke.spec.ts`; ráfaga y k6 ⚠️ sin test automatizado.

### REQ-OPS-010 Runbooks operativos

DEBEN existir runbooks para "pedido que no aparece" (`docs/runbooks/missing-order.md`, de MongoDB a
Kafka, DLT, reproceso y relay) y "caché que no se invalida"
(`docs/runbooks/cache-invalidation-lag.md`), enlazados desde las alertas.

- **Escenario: cliente bloqueado sigue aprobando**
  - Dado la alerta `MasterDataOutboxAging`
  - Cuando el operador abre el runbook enlazado
  - Entonces sigue el camino PATCH → outbox → relay → tópico → listener → Redis

Trazabilidad: ⚠️ sin test (`infra/prometheus/alerts.yml`, anotación `runbook`).
