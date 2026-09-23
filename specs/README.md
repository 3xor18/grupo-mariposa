# Especificaciones (Spec-Driven Design)

## Qué es SDD aquí

Las specs de esta carpeta describen el **comportamiento vigente** de la plataforma como requisitos
verificables. Son la fuente de verdad para decidir si un cambio es nuevo, si contradice algo o si
rompe un contrato, y se leen antes que el código.

- `constitution.md`: principios no negociables (`CON-xx`) que ningún cambio puede violar en
  silencio.
- Una spec por capacidad, con requisitos `REQ-<ÁREA>-NNN` normativos (DEBE / NO DEBE).
- Cada requisito tiene escenarios **Dado / Cuando / Entonces** con valores reales de muestras y
  pruebas, y una línea `Trazabilidad:` hacia las pruebas, contratos o ADRs que lo sostienen.
- Todo cambio de comportamiento empieza como propuesta en `changes/`, se aprueba y recién entonces
  se implementa; al mergear, la spec se actualiza y la propuesta se archiva.
- Lo que el código hace pero ninguna prueba verifica queda marcado `⚠️ sin test` y listado abajo.

## Cómo leer una spec

1. **Propósito**, **Alcance** y **Fuera de alcance**: qué cubre y dónde buscar lo demás.
2. **Requisitos**: el enunciado es el contrato; los escenarios son ejemplos concretos, no la lista
   completa de casos.
3. **Trazabilidad**: nombres de prueba verificables. Convenciones de rutas:
   - Clases Java sin ruta (`OrderProcessingIT`, `LinePricerTest`) viven en
     `order-processor/src/test/java/com/grupomariposa/orders/**`; `.../` abrevia ese prefijo.
   - `*.feature` viven en `e2e/karate/src/test/resources/mariposa/e2e/`.
   - `*.spec.ts` / `*.e2e-spec.ts` son de `clients-api/src/**` y `clients-api/test/`.
   - `*_test.go` son de `products-api/internal/**`.
   - `*_test.dart` son de `order-tracker/test/**`; `smoke.spec.ts` de `order-tracker/e2e/tests/`.
4. Los valores por defecto se citan con su variable (`HTTP_READ_TIMEOUT` 2 s). Los secretos se
   nombran, nunca se escriben.

## Esquema de identificadores

`REQ-<ÁREA>-NNN`, numeración de tres dígitos por área, sin reutilizar números retirados.

| Área | Capacidad | Spec |
|---|---|---|
| OP | Pedidos: validación, idempotencia, DLT, outbox, consulta | `order-processing.md` |
| TAX | Precios, descuentos, IVA con vigencia y API de tasas | `pricing-and-taxes.md` |
| MKT | Catálogo de mercados y monedas | `markets.md` |
| CLI | `clients-api` | `clients.md` |
| PRD | `products-api` | `products.md` |
| MDE | Eventos de datos maestros y caché | `master-data-events-and-cache.md` |
| EVT | Eventos, contratos y compatibilidad | `events-and-contracts.md` |
| SEC | Seguridad | `security.md` |
| CFG | Configuración | `configuration.md` |
| UI | PWA `order-tracker` | `order-tracker.md` |
| OBS | Observabilidad | `observability.md` |
| OPS | Operación, CI y despliegue | `operations.md` |

## Proceso de cambio

1. **Propuesta**: copiar `changes/_template.md` a `changes/<yyyy-mm-dd>-<slug>.md` con motivación,
   contraste con la constitución, requisitos `ADDED` / `MODIFIED` / `REMOVED` (IDs nuevos con el
   siguiente número libre del área), preguntas abiertas, impacto (servicios, contratos, eventos,
   `config-repo`, datos, seguridad, observabilidad, ADR) y plan de pruebas.
2. **Aprobación**: las preguntas abiertas se responden y la propuesta se aprueba explícitamente
   (estado `aprobado`, con quién y cuándo). Un conflicto con un `CON-xx` sólo avanza si la propuesta
   enmienda la constitución con motivo.
3. **Implementación**: cada prueba nueva cita el requisito que verifica (nombre de la prueba,
   `@DisplayName` o tag Karate `@REQ-<ÁREA>-NNN`). Los contratos cambian primero en `contracts/`.
4. **Revisión**: el PR verifica que cada requisito `ADDED`/`MODIFIED` tiene prueba y se cumple, y
   que ningún principio de la constitución se viola.
5. **Integración**: al mergear, el delta se integra en la spec de la capacidad (enunciados,
   escenarios, `Trazabilidad:`), se actualizan la tabla de abajo y las brechas, y la propuesta se
   mueve a `changes/archive/`.

## Índice de trazabilidad

| Spec | Requisitos | Nº | ⚠️ |
|---|---|---|---|
| `order-processing.md` | REQ-OP-001 … 019 | 19 | 0 |
| `pricing-and-taxes.md` | REQ-TAX-001 … 019 | 19 | 1 |
| `markets.md` | REQ-MKT-001 … 007 | 7 | 0 |
| `clients.md` | REQ-CLI-001 … 014 | 14 | 0 |
| `products.md` | REQ-PRD-001 … 012 | 12 | 0 |
| `master-data-events-and-cache.md` | REQ-MDE-001 … 010 | 10 | 0 |
| `events-and-contracts.md` | REQ-EVT-001 … 010 | 10 | 2 |
| `security.md` | REQ-SEC-001 … 015 | 15 | 1 |
| `configuration.md` | REQ-CFG-001 … 010 | 10 | 3 |
| `order-tracker.md` | REQ-UI-001 … 014 | 14 | 2 |
| `observability.md` | REQ-OBS-001 … 011 | 11 | 5 |
| `operations.md` | REQ-OPS-001 … 010 | 10 | 5 |
| **Total** | | **151** | **19** |

Pruebas que más requisitos sostienen:

- OP: `OrderProcessingIT`, `ConcurrencyIT`, `OutboxLeaseIT`, `validation.feature`,
  `idempotency.feature`, `resilience.feature`, `orders-api.feature`.
- TAX: `LinePricerTest`, `OrderEvaluatorTest`, `TaxRateApprovalsTest`, `TaxRateAdminServiceTest`,
  `OrderEnricherTaxRatesTest`, `TaxRatesApiIT`, `tax-rates.feature`.
- MKT: `MarketTest`, `ConfigurationSupportTest`, `markets.feature`, `catalog_test.go`,
  `market-catalog.spec.ts`.
- CLI: `clients.e2e-spec.ts`, `mongo-persistence.e2e-spec.ts`, `resilience.e2e-spec.ts`,
  `clients-api.feature`.
- PRD: `update_test.go`, `products_test.go`, `guards_test.go`, `integration_test.go`,
  `products-api.feature`.
- MDE: `MasterDataCacheIT`, `VersionedRedisStoreIT`, `VersionedCacheTest`,
  `master-data-cache.feature`.
- EVT: `OutboxPayloadFactoryTest`, `OrderDeadLetterPublisherTest`, `contract_test.go`,
  `scripts/ci/check-contracts.sh`.
- SEC: `WebSupportTest`, `OrdersControllerTest`, `AesGcmPiiCipherTest`, `DltSupportTest`,
  `verifier_test.go` y las features de las APIs.
- CFG: `OperationalBudgetTest`, `load-remote-config.spec.ts`, `remote_test.go`,
  `order-tracker-config.test.sh`.
- UI: `smoke.spec.ts`, pruebas de blocs, `order_dto_mapper_test.dart`, `money_test.dart`,
  `app_formatters_test.dart`.
- OBS: `ObservabilityTest`, `observability_test.go`, `OrdersApiIT`.
- OPS: `.github/workflows/ci.yml`, `scripts/ci/validate-helm.sh`, `app_test.go`.

## Brechas conocidas

### Requisitos sin prueba (`⚠️ sin test`, total o parcial)

| REQ | Qué falta |
|---|---|
| TAX-017 | Revisiones concurrentes de tasas (candado `tax_rate_guards`, `version`, índice único) |
| EVT-004 | Deduplicación por `eventId` en consumidores (no hay consumidor en el repo) |
| EVT-008 | Particiones, compactación y retención de tópicos |
| SEC-014 | Fallo del chart con Ingress sin `allowedCidrs`; NetworkPolicy efectiva |
| CFG-004 | Ausencia de secretos y hosts en `config-repo` (sin escáner automático) |
| CFG-007 | Inmutabilidad de la configuración en ejecución |
| CFG-009 | Backends `git` y `awsparamstore` del config server |
| UI-012 | Proxy `502 BAD_GATEWAY` y cabeceras de seguridad de nginx |
| UI-014 | Reglas de caché del service worker |
| OBS-007 | Exportación de trazas OTLP |
| OBS-008 | Reglas de alerta (sin `promtool test rules`) |
| OBS-009 | Dashboards de Grafana |
| OBS-010 | `load/demo-traffic.sh` |
| OBS-011 | Perfil opcional de Datadog |
| OPS-001 | `mariposa.sh init/up` (generación y actualización de `.env`) |
| OPS-003 | Comandos de `mariposa.sh` salvo `e2e` |
| OPS-006 | Despliegue (`deploy.yml`, `resolve-services.sh`, `deploy-eks.sh`, Jenkins) |
| OPS-009 | Ráfaga de eventos y k6 |
| OPS-010 | Runbooks |

### Ambigüedades y comportamiento no definido

1. **Exposición de `/tax-rates` en EKS**: `order-processor` no tiene Ingress y su NetworkPolicy sólo
   admite a `order-tracker`, pero el proxy `/api/` de nginx no restringe métodos, así que
   `POST /api/tax-rates` es alcanzable por el Ingress público de la PWA con un token `orders-admin`.
   No está definido si es intencional.
2. **Aprobación tardía**: la validación de `validFrom` futuro sólo ocurre al proponer; aprobar
   después de `validFrom` se acepta y los pedidos ya procesados no se recalculan. No está definido
   el tratamiento retroactivo.
3. **Revocar o acotar tasas**: no hay forma de cancelar una tasa aprobada; un periodo con `validTo`
   sólo se aprueba si ya existe su sucesor, y no se puede insertar una tasa temporal entre periodos
   aprobados (la aprobación no recorta periodos cerrados).
4. **`occurredAt` del productor sin límites**: un `occurredAt` futuro elige una tasa futura; uno
   anterior al inicio del cronograma se ajusta al inicio. No hay regla de negocio que lo acote.
5. **Ventana entre pods**: la aprobación refresca sólo la instancia que la atiende; las demás tardan
   hasta 30 s (TODO-6 del roadmap, riesgo aceptado en ADR 0008) y no hay anticipación mínima.
6. **Autenticación apagada y cuatro ojos**: con `AUTH_ENABLED=false` (perfil `local`) todas las
   solicitudes comparten el mismo actor, por lo que ninguna aprobación es posible.
7. **Rechazo por el proponente**: el proponente puede rechazar su propia propuesta; no está definido
   si es un "retiro" legítimo o debería exigir otro revisor.
8. **Contrato vs implementación**: `order-processor.openapi.yaml` declara `429` en `/orders` pero
   `order-processor` no limita tasa; `Money` dice "two decimals" aunque `CLP` usa 0.
9. **Códigos de ruta desconocida**: `NOT_FOUND` en `order-processor` y `clients-api`,
   `RESOURCE_NOT_FOUND` en `products-api`.
10. **Evolución de JSON Schema**: CI compila esquemas y valida muestras, pero no detecta cambios
    incompatibles entre versiones de un esquema de evento (la propuesta de arquitectura lo
    prometía); oasdiff sólo corre en PRs.
11. **Secretos**: no hay escaneo automático de secretos en CI; CON-01 depende de `.gitignore` y
    revisión.
12. **Documentación desalineada**: `contracts/events/orders.processing.dlt.md` limita la moneda a
    "MX-MXN, CO-COP, PE-PEN"; `config-repo/README.md` dice que agregar un país requiere código;
    `docs/runbooks/missing-order.md` menciona un resultado `DUPLICATE` de inbox que no existe (sólo
    `APPROVED`, `REJECTED`, `STALE`); `README.md` cita 100 % de cobertura en `products-api` y el
    gate de CI es 95 %.
13. **Unidad de tasas en el contrato**: `discountRate` y `taxRate` no declaran unidad; la PWA asume
    fracciones (0.16 = 16 %).
14. **Lectura de API más vieja que el piso de caché**: si la entrada sólo guarda la versión (sin
    datos), una lectura de la API con versión menor se usa para decidir el pedido sin cachearse.
15. **Alertas sin runbook**: `TechnicalFailuresRising`, `ProcessingLatencyP95High` y
    `CircuitBreakerOpen` no enlazan runbook.
16. **CON-12 en scripts**: `load/demo-traffic.sh` tiene líneas de más de 100 columnas; no hay lint
    de longitud para shell.
17. **Paginación por offset**: `GET /orders` puede saltar filas cuando llegan pedidos nuevos entre
    páginas; `GET /tax-rates` no pagina.
18. **Operación pendiente**: reproceso de la DLT manual y sin herramienta; sin job de re-cifrado
    tras rotar la llave de PII.
