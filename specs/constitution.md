# Constitución de la plataforma

Principios **no negociables**. Toda propuesta de cambio (`specs/changes/`) se contrasta primero
contra esta lista. Un cambio que viole un principio no se implementa salvo que la propuesta enmiende
la constitución de forma explícita, con motivo y aprobación registrados.

Cada principio indica **cómo se verifica** hoy en el repositorio.

## CON-01 Sin secretos en el repositorio

Ningún secreto (contraseñas, llaves, tokens, secretos de cliente OAuth, llaves de PII) DEBE vivir en
el código, en `config-repo/`, en los values de Helm ni en capas de imagen. Los secretos llegan sólo
por variables de entorno: `.env` generado en local (nunca commiteado), GitHub Secrets en CI y AWS
Secrets Manager vía External Secrets en EKS. Las specs nombran la variable, nunca el valor.

- Cómo se verifica: `.env` en `.gitignore` y `git ls-files` sólo lista `.env.example` sin valores;
  `ConfigurationSupportTest.should_read_secrets_only_from_the_environment`; revisión de PR
  (no hay escáner automático de secretos, ver Brechas en `README.md`).

## CON-02 Todo es configurable con precedencia única

Todo parámetro de negocio o técnico DEBE poder cambiarse sin recompilar, con la precedencia
**variable de entorno > config server > valor por defecto** en los cuatro componentes.

- Cómo se verifica: placeholders `${VAR:default}` en
  `order-processor/src/main/resources/application.yml`; `load-remote-config.spec.ts`
  (`should_merge_with_precedence_environment_then_remote_then_defaults`),
  `products-api/internal/config/remote/remote_test.go` (`TestResolvePrecedence`) y
  `order-tracker/nginx/tests/order-tracker-config.test.sh`.

## CON-03 Arquitectura hexagonal verificada

El dominio DEBE ser Java puro (sin Spring, Kafka, Mongo ni HTTP); la aplicación sólo depende del
dominio y de sus puertos; los adaptadores viven en `infrastructure`; los puertos de entrada son
interfaces; no hay ciclos. Las APIs repiten la forma transporte → servicio → puerto de repositorio.

- Cómo se verifica: `order-processor/.../architecture/ArchitectureTest.java` (ArchUnit) en
  `./mvnw verify`.

## CON-04 Cobertura como gate de build

`order-processor` DEBE tener 100 % de líneas en `domain` y `application` y ≥ 90 % global;
`clients-api` 100 % de líneas, ramas y funciones; `order-tracker` 100 % de líneas de `lib/`
(excepto el bootstrap web); `config-server` 100 %; `products-api` sobre el umbral de CI.

- Cómo se verifica: JaCoCo en `order-processor/pom.xml` (`coverage.core.minimum=1.00`,
  `coverage.overall.minimum=0.90`); `npm run test:cov`; `scripts/ci/check-lcov-coverage.sh`;
  `scripts/ci/check-go-coverage.sh` con `GO_COVERAGE_MIN` en `.github/workflows/ci.yml`.

## CON-05 Idempotencia y orden por pedido

Un `eventId` DEBE producir como máximo un efecto de negocio; un `(orderId, eventVersion)` DEBE tener
un único resultado; una versión menor NO DEBE pisar una mayor; los eventos de un mismo pedido o de
una misma entidad maestra DEBEN publicarse en orden de versión. La garantía vive en restricciones
atómicas de la base de datos, no en locks de aplicación ni en el orden de Kafka.

- Cómo se verifica: `ConcurrencyIT`, `OrderProcessingIT`, `OutboxLeaseIT`,
  `e2e/karate/.../idempotency.feature`.

## CON-06 Consistencia outbox / inbox

Todo cambio de estado que deba anunciarse (pedido decidido, cliente o producto modificado) DEBE
escribirse en la **misma transacción** que su evento de outbox. Ningún evento DEBE publicarse sin su
cambio confirmado ni quedar un cambio confirmado sin evento pendiente. La entrada se deduplica con
`inbox` (`_id = eventId`) en la misma transacción.

- Cómo se verifica: `PersistenceIT`, `OutboxRelayIT`, `mongo-persistence.e2e-spec.ts`
  (`should_roll_back_the_client_change_when_the_outbox_write_fails`),
  `products-api/internal/app/integration_test.go` (`TestPatchPublishesChangeEventThroughOutbox`),
  ADR 0002 y ADR 0007.

## CON-07 Sin datos personales en eventos ni logs

El nombre del cliente (PII) NO DEBE viajar en ningún evento Kafka ni aparecer en logs, headers de la
DLT o respuestas de error. En reposo se guarda cifrado (AES-256-GCM). Las causas de error se sanean
(tokens, secretos, correos) antes de salir del proceso.

- Cómo se verifica: esquemas `orders.processed.v1` y `clients.changed.v1` sin `name`;
  `client-changed.event.spec.ts`; `DltSupportTest`; `AesGcmPiiCipherTest`;
  `OrderDocumentMapperTest.should_store_money_as_decimal128_and_encrypt_client_name`.

## CON-08 JWT con rol y audiencia en toda API

Toda API HTTP de negocio DEBE exigir un JWT de Keycloak válido (firma JWKS, `iss`, `exp`) con la
**audiencia** del servicio y el **rol de realm** del endpoint. Sin token → `401`; sin rol → `403`.
Sólo health, métricas y (si se habilita) la documentación quedan sin autenticación.

- Cómo se verifica: `orders-api.feature`, `products-api.feature`, `clients-api.feature`,
  `master-data-cache.feature`; `WebSupportTest.should_fail_closed_without_audience_while_...`;
  `verifier_test.go` (`TestVerifyAlwaysChecksAudience`).

## CON-09 Contratos versionados y compatibles hacia atrás

Los contratos viven en `contracts/` (OpenAPI 3.1, JSON Schema 2020-12, RFC 9457). Dentro de una
versión sólo se admiten cambios aditivos; un cambio incompatible DEBE crear un tópico `.v2` o una
ruta `/v2` con migración *expand → migrate → contract*. Los consumidores son *tolerant readers*.

- Cómo se verifica: `scripts/ci/check-contracts.sh` (oasdiff `--fail-on ERR` contra la rama base,
  `ajv` sobre esquemas y ejemplos) en el job `contracts` de CI; tests de contrato de cada servicio.

## CON-10 Dinero exacto y redondeo HALF_UP por moneda

Todo importe DEBE calcularse con aritmética decimal exacta (`BigDecimal` en Java, unidades enteras
en la PWA), redondear **HALF_UP a los decimales de la moneda** (ISO 4217, `CLP` = 0) en cada importe
de línea, y los totales DEBEN ser la suma de los importes ya redondeados.

- Cómo se verifica: `MoneyTest`, `LinePricerTest`, `TotalsTest`, `OrderEvaluatorTest`,
  `markets.feature`, `money_test.dart`.

## CON-11 Observabilidad de serie

Todo servicio DEBE exponer métricas Prometheus, health `live`/`ready`, logs JSON estructurados con
`traceId` (y `orderId`/`eventId` al procesar pedidos) y propagar `traceparent` W3C.

- Cómo se verifica: `ObservabilityTest`, `OrdersApiIT.should_expose_probes_and_metrics_...`,
  `observability_test.go`, `platform.e2e-spec.ts`.

## CON-12 Código limpio

Sin comentarios en el código (la documentación vive en `docs/`, ADRs, OpenAPI y `specs/`); sin
literales mágicos (constantes, enums o propiedades tipadas); líneas ≤ 100 columnas; funciones cortas
(ideal ≤ 20 líneas, máximo de Checkstyle 30) con un solo nivel de abstracción; inmutabilidad por
defecto; nunca tragar excepciones.

- Cómo se verifica: Checkstyle (`LineLength` 100, prohibición de comentarios, `MethodLength` 30) y
  SpotBugs en `order-processor`; ESLint + Prettier en `clients-api`; `golangci-lint`;
  `dart format --line-length 100` y `flutter analyze` en CI.

## CON-13 Contrato de error único

Todo error HTTP DEBE responder `application/problem+json` según
`contracts/common/problem.schema.json` (ADR 0004) con `code` estable y `traceId`. Los consumidores
deciden por `status` y `code`, nunca por `detail`. Un error inesperado NUNCA se responde como `4xx`
ni expone detalles internos.

- Cómo se verifica: `OrdersApiIT.should_answer_problem_details_for_errors`,
  `contract_test.go` (`TestContractProblemResponses`), `contract.e2e-spec.ts`, `problem.json` de
  Karate.

## CON-14 Contenedores mínimos y sin root

Toda imagen DEBE ser multi-stage, correr con usuario no root, tener `HEALTHCHECK` y no llevar
secretos en capas. Las imágenes con vulnerabilidades `CRITICAL`/`HIGH` corregibles no se publican.

- Cómo se verifica: `USER` no root en los cinco `Dockerfile`; job `images` de CI con Trivy
  (`exit-code: 1`); `scripts/ci/scan-image.sh` antes del push en despliegue.
