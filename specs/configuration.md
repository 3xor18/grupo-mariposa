# Configuración (CFG)

## Propósito

Toda la plataforma se configura desde un único lugar auditable (`config-repo/`, servido por
`config-server`), con precedencia uniforme y validación al arrancar, sin secretos en el repositorio
y sin recompilar para cambiar un parámetro.

## Alcance

- Precedencia, carga remota en los cuatro componentes, `config-server` y sus backends, perfiles,
  validación al arrancar, presupuestos operativos de `order-processor` e inmutabilidad.

## Fuera de alcance

- Catálogo de mercados (`markets.md`) y tasas (`pricing-and-taxes.md`), secretos (`security.md`).

## Requisitos

### REQ-CFG-001 Precedencia única

En los cuatro componentes el valor efectivo DEBE resolverse como **variable de entorno > config
server > valor por defecto**. Una variable definida, aun vacía, DEBE ganar sobre el config server en
`clients-api` y `products-api` (en `products-api` un valor vacío significa "usar el por defecto").

- **Escenario: sobrescritura por entorno**
  - Dado `RATE_LIMIT_RPS` definido en el entorno y `rate-limit.rps` en el config server
  - Cuando arranca `clients-api`
  - Entonces usa el valor del entorno

Trazabilidad: `load-remote-config.spec.ts`
(`should_merge_with_precedence_environment_then_remote_then_defaults`,
`should_fall_through_to_remote_only_when_environment_variable_is_undefined`),
`remote_test.go` (`TestResolvePrecedence`),
`order-tracker/nginx/tests/order-tracker-config.test.sh`.

### REQ-CFG-002 Config server opcional y fallo rápido configurable

Con `CONFIG_SERVER_URL` vacío el cliente de configuración DEBE desactivarse y el servicio arrancar
sólo con entorno y valores por defecto. Con URL, si el servidor no responde tras los reintentos,
DEBE abortar el arranque si `CONFIG_SERVER_FAIL_FAST=true` y continuar con una advertencia si es
`false`. Por defecto es `false` en `order-processor`, `products-api` y `clients-api`, y `true` en
el contenedor de `order-tracker`; en EKS los servicios lo fijan en `true`.

- **Escenario: sin URL**
  - Dado `CONFIG_SERVER_URL` vacío
  - Cuando arranca `order-processor`
  - Entonces `spring.cloud.config.enabled=false`
- **Escenario: servidor caído sin fail-fast**
  - Dado `CONFIG_SERVER_FAIL_FAST=false` y el servidor inaccesible
  - Cuando arranca `clients-api`
  - Entonces registra una advertencia y sigue con el entorno

Trazabilidad: `ConfigurationSupportTest.should_disable_config_server_when_no_url_is_given`,
`load-remote-config.spec.ts` (`should_throw_when_unreachable_and_fail_fast_is_enabled`,
`should_warn_and_continue_with_environment_when_fail_fast_is_disabled`), `remote_test.go`
(`TestResolveUnreachable`, `TestResolveSkipsWithoutURL`), `order-tracker-config.test.sh`.

### REQ-CFG-003 Formato remoto para servicios no Spring

`products-api`, `clients-api` y `order-tracker` DEBEN leer
`${CONFIG_SERVER_URL}/${CONFIG_APP_NAME}-${CONFIG_PROFILE}.properties` con autenticación básica,
reintentos con backoff y jitter, parseo según `java.util.Properties` y mapeo a nombre de entorno
(`rate-limit.rps` → `RATE_LIMIT_RPS`, `platform.markets` → `PLATFORM_MARKETS`). Las claves
desconocidas DEBEN ignorarse. Los logs DEBEN listar sólo nombres de claves, nunca valores ni
credenciales.

- **Escenario: separadores y escapes**
  - Dado `auth.jwks-url: http://host:8080/...`
  - Cuando se parsea
  - Entonces la URL queda intacta y la clave es `AUTH_JWKS_URL`

Trazabilidad: `properties.spec.ts`, `config-server-client.spec.ts`, `properties_test.go`
(`TestParseProperties`, `TestEnvKey`), `load-remote-config.spec.ts`
(`should_log_only_loaded_keys_never_values_or_credentials`), `order-tracker-config.test.sh`.

### REQ-CFG-004 Contenido de `config-repo`

`config-repo/` DEBE contener sólo parámetros de negocio y técnicos: `application.yml` (catálogo
común), `application-docker.yml`, `<servicio>.yml`, `<servicio>-docker.yml`, `-staging.yml` y
`-production.yml`. NO DEBE contener secretos ni hosts de un ambiente (éstos viven en
`deploy/helm/values/<ambiente>/`). Un cambio es un PR revisado.

- **Escenario: nuevo parámetro**
  - Dado un cambio de `HTTP_READ_TIMEOUT`
  - Cuando se propone
  - Entonces se edita `config-repo/order-processor.yml` por PR, sin credenciales

Trazabilidad: ⚠️ sin test (revisión manual; no hay escaneo automático de secretos).

### REQ-CFG-005 Validación completa al arrancar

Cada servicio DEBE validar su configuración tipada al arrancar y NO DEBE iniciar con un valor
inválido: `order-processor` con `@ConfigurationProperties` validados; `products-api` listando todas
las variables inválidas; `clients-api` con esquema estricto y log `fatal`.

- **Escenario: valores inválidos en Go**
  - Dado varias variables fuera de rango
  - Cuando arranca `products-api`
  - Entonces se niega a arrancar listando cada variable inválida

Trazabilidad: `config_test.go` (`TestLoadRejectsInvalidValues`), `load-config.spec.ts`,
`CachePropertiesTest`, `ApplicationModelTest.should_reject_unsafe_relay_settings`.

### REQ-CFG-006 Presupuestos operativos coherentes

`order-processor` NO DEBE arrancar si: el fan-out (`PROCESSING_MAX_CONCURRENT_LOOKUPS`) supera el
bulkhead; `max.poll.records × presupuesto por intento + backoff de registro ≥ max.poll.interval.ms`;
`delivery.timeout.ms > OUTBOX_SEND_TIMEOUT`; o `OUTBOX_SEND_TIMEOUT ≥ OUTBOX_LEASE`.

- **Escenario: valores por defecto**
  - Dado la configuración por defecto (10 registros, 3 intentos de 2.5 s, poll de 300 s)
  - Cuando se verifica el presupuesto
  - Entonces no hay violaciones

Trazabilidad: `OperationalBudgetTest` (`should_accept_default_budget`,
`should_report_every_unsafe_combination`).

### REQ-CFG-007 Configuración inmutable en ejecución

La configuración leída al arrancar DEBE ser inmutable: un cambio en `config-repo` se aplica con un
reinicio progresivo (`kubectl rollout restart`). La única excepción son las tasas de IVA, que se
administran en la base y se refrescan en caliente (`pricing-and-taxes.md`).

- **Escenario: cambio de timeout**
  - Dado un nuevo `HTTP_READ_TIMEOUT` en `config-repo`
  - Cuando no se reinicia el servicio
  - Entonces el valor anterior sigue vigente

Trazabilidad: ⚠️ sin test.

### REQ-CFG-008 Perfiles por ambiente

El perfil `docker` DEBE activar inyección de fallos, semilla, documentación de API, replicación 1 y
muestreo de trazas 1.0; `staging` y `production` DEBEN desactivar inyección de fallos, semilla y
documentación, con replicación 3 y muestreo 0.25 / 0.1. En EKS los overlays de Helm fijan además
`KAFKA_TLS_ENABLED=true`.

- **Escenario: fallos sólo en local**
  - Dado el perfil `production`
  - Cuando se lee `fault.injection-enabled`
  - Entonces es `false`

Trazabilidad: `config-repo/*-docker.yml`, `*-staging.yml`, `*-production.yml`; guardas en
`config_test.go` (`TestLoadGuards`) y `load-config.spec.ts`
(`should_refuse_unsafe_switches_in_production`).

### REQ-CFG-009 Backends del config server

`config-server` DEBE servir `config-repo` con el backend `native` en local, `git` en EKS (rama
`main`, `search-paths: config-repo`, credenciales de External Secrets) o `awsparamstore` (prefijo
`/grupo-mariposa`), seleccionado con `CONFIG_BACKEND`.

- **Escenario: backend nativo**
  - Dado el backend `native` sobre un repositorio de prueba
  - Cuando se pide `/sample-service/docker` con credenciales
  - Entonces responde la configuración

Trazabilidad: `ConfigServerApplicationTest.should_serve_configuration_when_credentials_are_valid`;
backends `git` y `awsparamstore` ⚠️ sin test.

### REQ-CFG-010 Límites del contrato como constantes

Los límites que reflejan el JSON Schema de entrada (64 caracteres por id, 32 de canal, 500 ítems,
256 caracteres de causa) DEBEN coincidir con el contrato y NO son parámetros configurables; los
patrones de id y los límites de precio sí lo son (`VALIDATION_*`).

- **Escenario: 501 ítems**
  - Dado un pedido con 501 ítems
  - Cuando se valida
  - Entonces falla con "must contain at most 500 items"

Trazabilidad: `OrderCommandValidatorTest` (`should_limit_item_count`,
`should_limit_identifier_and_channel_lengths`, `should_reject_unsafe_contract_rules`).
