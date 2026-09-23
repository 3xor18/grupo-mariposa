# Observabilidad (OBS)

## Propósito

Poder responder "¿qué pasó con este pedido?" y "¿está sana la plataforma?" sin entrar al código:
logs JSON correlacionados, métricas Prometheus con nombres estables, trazas OTLP, health checks,
alertas con runbook y dos dashboards de Grafana, más un generador de tráfico para demostrarlo.

## Alcance

- Logs, correlación, métricas de `order-processor` y de las APIs, trazas, health, alertas,
  dashboards y tráfico de demostración.

## Fuera de alcance

- Qué eventos de negocio ocurren (`order-processing.md`), operación de la plataforma
  (`operations.md`).

## Requisitos

### REQ-OBS-001 Logs estructurados y correlacionados

Cada servicio DEBE escribir logs JSON a stdout (ECS en Java, pino en NestJS, `slog` en Go) con
nivel, servicio y `traceId`; `order-processor` DEBE agregar `orderId` y `eventId` (MDC) mientras
procesa un registro y restaurar el contexto previo al terminar. Las etapas terminales DEBEN
registrarse en `INFO` y las intermedias en `DEBUG`. NUNCA DEBEN loguearse tokens, secretos ni
nombres de clientes.

- **Escenario: contexto anidado**
  - Dado un contexto de log con `orderId` A
  - Cuando se procesa un evento del pedido B y termina
  - Entonces el contexto vuelve a A

Trazabilidad: `ObservabilityTest.should_restore_previous_log_context`,
`observability_test.go` (`TestAccessLogAndMetrics`), `bootstrap-logger` en
`load-remote-config.spec.ts` (`should_write_structured_json_lines_with_service_and_context`).

### REQ-OBS-002 Correlación W3C

Cada servicio HTTP DEBE aceptar y propagar `traceparent` (W3C) y `X-Request-Id`, generar un trace id
si no llega o es inválido, devolverlo en la respuesta y en todo `Problem` (`traceId`).

- **Escenario: `traceparent` inválido**
  - Dado un `traceparent` mal formado
  - Cuando llega a `products-api`
  - Entonces se reemplaza por uno nuevo que aparece en la respuesta y en los logs

Trazabilidad: `observability_test.go` (`TestTraceparentIsPropagated`,
`TestInvalidTraceparentIsReplaced`, `TestRequestIDIsEchoedWhenValid`), `clients.e2e-spec.ts`
(`should_echo_request_id_but_keep_a_w3c_trace_id_when_only_request_id_is_sent`),
`WebSupportTest.should_build_problem_with_fallback_instance_and_generated_trace`.

### REQ-OBS-003 Métricas de negocio y procesamiento

`order-processor` DEBE exponer en `/actuator/prometheus`, con tag `service`:
`orders_processed_total{status,market}`, `orders_rejected_total{reason,market}`,
`orders_amount_total{currency,market}` y `orders_lines_total{market}` (sólo aprobados),
`orders_duplicates_total`, `orders_stale_total`, `orders_conflicts_total`,
`orders_technical_failures_total{category}`, `orders_dlt_total{category}`,
`orders_retries_total{dependency}`, `orders_stages_total{stage}` y el histograma
`orders_processing_latency_seconds`.

- **Escenario: pedido aprobado**
  - Dado un pedido MX aprobado por 2100.11 MXN con 2 líneas
  - Cuando se registra el resultado
  - Entonces `orders_processed_total{status="APPROVED",market="MX"}` sube 1,
    `orders_amount_total{currency="MXN",market="MX"}` sube 2100.11 y `orders_lines_total` 2

Trazabilidad: `ObservabilityTest` (`should_count_every_outcome_kind`,
`should_count_every_stage_and_classify_terminal_ones`, `should_record_latency_retries_and_...`).

### REQ-OBS-004 Métricas de outbox, caché y tasas

`order-processor` DEBE exponer `outbox_pending` y `outbox_oldest_age_seconds` (consultados en cada
scrape; `NaN` si MongoDB no responde), `orders_outbox_published_total`,
`orders_outbox_failures_total`, `orders_outbox_lease_lost_total`,
`orders_outbox_relay_failures_total`, `orders_cache_hits_total` /
`orders_cache_misses_total{cache}`, `orders_cache_errors_total{cache,operation}`,
`orders_cache_invalidations_total{cache,outcome=applied|stale|ignored|error}`,
`orders_tax_rates_fallback` (1 mientras se usa la tabla de configuración) y
`orders_tax_rates_refresh_failures_total`. Las APIs DEBEN exponer `outbox_pending`,
`outbox_oldest_age_seconds`, `outbox_published_total` y `outbox_publish_failures_total` con los
mismos nombres.

- **Escenario: MongoDB caído durante el scrape**
  - Dado MongoDB inaccesible
  - Cuando Prometheus lee `outbox_pending`
  - Entonces obtiene `NaN` y el scrape no falla

Trazabilidad: `ObservabilityTest` (`should_expose_outbox_gauges_and_degrade_to_nan`,
`should_count_outbox_publications`), `VersionedCacheTest.should_count_event_outcomes`,
`RefreshingTaxRateSourceTest`, `outbox-metrics.spec.ts` (`should_use_the_platform_metric_names`),
`storage/mongodb/outbox_test.go` (`TestBacklogReportsPendingAndOldest`).

### REQ-OBS-005 Métricas HTTP de las APIs

`products-api` DEBE exponer `http_server_requests_total` y `http_server_request_duration_seconds`
con `method` normalizado (`GET`, `HEAD`, `POST`, `PUT`, `PATCH`, `DELETE`, `OPTIONS` u `OTHER`),
`route` (patrón) y `status`; `clients-api` métricas por ruta y estado en `/metrics`;
`order-processor` histogramas de `http.server.requests`.

- **Escenario: método raro**
  - Dado una solicitud con método `PROPFIND`
  - Cuando se registra
  - Entonces la etiqueta `method` es `OTHER`

Trazabilidad: `observability_test.go` (`TestMethodIsNormalizedForMetricsAndLogs`),
`platform.e2e-spec.ts` (`should_expose_prometheus_metrics_by_route_and_status`).

### REQ-OBS-006 Health checks

Cada servicio DEBE exponer `GET /health/live` y `GET /health/ready` sin autenticación con
`{"status":"UP"}` (o `503` y `DOWN`). En `order-processor` la readiness DEBE incluir MongoDB y Kafka
(este último con `describeCluster` y timeout `KAFKA_HEALTH_TIMEOUT`), y existen además `/livez`,
`/readyz` y `/actuator/health/{liveness,readiness}`.

- **Escenario: sondas de order-processor**
  - Dado el servicio arriba
  - Cuando se consultan `/health/live`, `/health/ready` y `/actuator/prometheus` sin token
  - Entonces responden `200`

Trazabilidad: `OrdersApiIT.should_expose_probes_and_metrics_for_internal_scraping`,
`observability_test.go` (`TestHealthEndpoints`), `platform.e2e-spec.ts`, `products-api.feature`.

### REQ-OBS-007 Trazas distribuidas

`order-processor` DEBE exportar trazas OTLP (`OTEL_EXPORTER_OTLP_ENDPOINT`, Jaeger en local) con
muestreo `TRACING_SAMPLING_PROBABILITY` (0.1 por defecto; 1.0 en `docker`, 0.25 en staging, 0.1 en
producción) y observación en listener y `KafkaTemplate`. El evento de salida lleva el `traceparent`
del span del relay; la correlación de negocio se mantiene por `orderId`/`eventId`.

- **Escenario: pedido en Jaeger**
  - Dado el perfil `docker`
  - Cuando se procesa un pedido
  - Entonces su traza aparece en Jaeger

Trazabilidad: ⚠️ sin test.

### REQ-OBS-008 Alertas con runbook

Prometheus DEBE cargar `infra/prometheus/alerts.yml` con: `OutboxBacklogAging` (> 60 s por 2 min,
crítica), `DeadLetterRateHigh` (> 0.1/s por 5 min), `TechnicalFailuresRising` (> 0.05/s por 5 min),
`ProcessingLatencyP95High` (p95 > 1 s por 10 min), `ConsumerLagGrowing` (> 1000 por 5 min, crítica),
`CircuitBreakerOpen` (1 min, crítica), `MasterDataOutboxAging` (> 30 s por 2 min, crítica) y
`CacheInvalidationFailures` (`error`/`failed` > 0 por 5 min). `OutboxBacklogAging`,
`DeadLetterRateHigh` y `ConsumerLagGrowing` DEBEN enlazar `docs/runbooks/missing-order.md`; las dos
de datos maestros, `docs/runbooks/cache-invalidation-lag.md`.

- **Escenario: relay detenido**
  - Dado el outbox de `order-processor` sin publicar durante más de 60 s
  - Cuando pasan 2 minutos
  - Entonces dispara `OutboxBacklogAging` con el runbook de pedido perdido

Trazabilidad: ⚠️ sin test (no hay pruebas `promtool` de reglas).

### REQ-OBS-009 Dashboards de Grafana

Grafana DEBE aprovisionar "Grupo Mariposa — Procesamiento de pedidos" (uid `mariposa-orders`,
refresco 10 s) y "Demo entrevista — Plataforma de pedidos B2B" (uid `mariposa-demo`, refresco 5 s),
este último con filas Negocio (procesados, % aprobados, rechazados, fallos técnicos, duplicados
evitados, p95, por país, monto aprobado por moneda, motivos), Calidad del procesamiento, Resiliencia
(circuit breakers, outbox, reintentos), Caché y datos maestros (aciertos, invalidaciones y "Tabla de
IVA" desde `orders_tax_rates_fallback`) y APIs.

- **Escenario: tablero de demo**
  - Dado la plataforma arriba y tráfico de demo
  - Cuando se abre `http://localhost:3001/d/mariposa-demo`
  - Entonces los paneles muestran pedidos por resultado, país y moneda

Trazabilidad: ⚠️ sin test (`infra/grafana/dashboards/*.json`).

### REQ-OBS-010 Tráfico de demostración

`./mariposa.sh demo-traffic [minutos]` (10 por defecto) DEBE publicar lotes de `DEMO_BATCH_SIZE`
(12) pedidos cada `DEMO_PAUSE_SECONDS` (3 s) en los cinco mercados con clientes y productos semilla:
~12 % rechazos conocidos, ~4 % inválidos (moneda `XXX`), ~6 % duplicados exactos y, con
`DEMO_CHAOS=true`, ~4 % de casos de fallo de dependencias (`CLI-40002`, `PRD-013`).

- **Escenario: demo de 2 minutos con caos**
  - Dado `DEMO_CHAOS=true ./mariposa.sh demo-traffic 2`
  - Cuando termina
  - Entonces imprime el total enviado y el enlace al tablero `mariposa-demo`

Trazabilidad: ⚠️ sin test (`load/demo-traffic.sh`).

### REQ-OBS-011 Exportación opcional a Datadog

Con el perfil de Compose `datadog` y `DATADOG_ENABLED=true` (+ `DD_API_KEY`) las métricas DEBEN
exportarse también a Datadog; por defecto está desactivado.

- **Escenario: perfil apagado**
  - Dado `./mariposa.sh up` sin perfil
  - Cuando arranca
  - Entonces `datadog-agent` no se levanta

Trazabilidad: ⚠️ sin test.
