# Maestro de clientes: clients-api (CLI)

## Propósito

`clients-api` (NestJS) es el dueño de los datos de distribuidores: expone su lectura a
`order-processor`, permite a administradores cambiar estado, segmento y régimen con concurrencia
optimista, y anuncia cada cambio en `clients.changed.v1` con Transactional Outbox (ADR 0007).

## Alcance

- `GET /clients/{clientId}`, `PATCH /clients/{clientId}`, persistencia en la base `clients`,
  outbox y relay, semilla, rate limiting, inyección de fallos, health y guardas de producción.

## Fuera de alcance

- Cómo `order-processor` cachea los clientes (`master-data-events-and-cache.md`), validación JWT
  común (`security.md`), carga desde el config server (`configuration.md`).

## Requisitos

### REQ-CLI-001 Lectura de un cliente

`GET /clients/{clientId}` (rol `clients-reader`) DEBE responder `200` con `clientId`, `name`,
`status`, `segment`, `taxRegime`, `market` y `version`, y el header `ETag: "<version>"`. NO DEBE
exponer datos de contacto ni información fiscal más allá de `taxRegime`.

- **Escenario: cliente del ejemplo dorado**
  - Dado `CLI-99821`
  - Cuando `admin` lo consulta
  - Entonces responde `Distribuidora Central`, `ACTIVE`, `WHOLESALE`, `GENERAL`, `MX` y un
    `version` numérico

Trazabilidad: `clients-api.feature` (returns a client),
`clients-api/test/clients.e2e-spec.ts` (`should_return_client_when_it_exists`),
`contract.e2e-spec.ts`.

### REQ-CLI-002 Identificador inválido e inexistente

Un `clientId` que no cumple `^CLI-[A-Z0-9]{1,20}$` DEBE responder `400 VALIDATION_ERROR`; uno
inexistente `404 CLIENT_NOT_FOUND`, ambos como `application/problem+json`.

- **Escenario: id mal formado**
  - Dado `client-1`
  - Cuando se consulta
  - Entonces responde `400 VALIDATION_ERROR`
- **Escenario: cliente inexistente**
  - Dado `CLI-00000`
  - Cuando se consulta
  - Entonces responde `404` con `code=CLIENT_NOT_FOUND` según el contrato de problema

Trazabilidad: `clients-api.feature`, `clients.e2e-spec.ts`
(`should_return_404_client_not_found_when_client_does_not_exist`,
`should_return_400_for_invalid_...`).

### REQ-CLI-003 Actualización administrativa con versión

`PATCH /clients/{clientId}` (rol `clients-admin`) DEBE aceptar sólo `status` (`ACTIVE`, `BLOCKED`),
`segment` (`WHOLESALE`, `RETAIL`) y `taxRegime` (`GENERAL`, `SIMPLIFIED`, `EXEMPT`), al menos uno;
campos desconocidos o cuerpo vacío DEBEN responder `400`. Un cambio efectivo DEBE incrementar
`version` en 1 y responder `200` con la entidad y el nuevo `ETag`.

- **Escenario: bloquear el cliente demo**
  - Dado `CLI-70001` activo con `ETag "1"`
  - Cuando `admin` envía `{"status":"BLOCKED"}` con `If-Match: "1"`
  - Entonces responde `200`, `status=BLOCKED` y `ETag "2"`

Trazabilidad: `master-data-cache.feature` (blocking a cached client), `clients.e2e-spec.ts`
(`should_update_with_matching_version_and_return_the_new_etag`), `update-client.request.spec.ts`.

### REQ-CLI-004 Reglas de `If-Match`

`If-Match` ausente o `*` DEBE actualizar sin condición. Si no, es una lista separada por comas de
etiquetas: una etiqueta fuerte `"N"` (o `N`) coincide cuando `N` es la versión actual; las débiles
`W/"N"` y las opacas (`"abc"`) nunca coinciden. Una lista válida sin coincidencia DEBE responder
`412 PRECONDITION_FAILED` sin cambios; una lista mal formada (ítem vacío, comillas desbalanceadas,
`*` dentro de una lista) DEBE responder `400 VALIDATION_ERROR`.

- **Escenario: versión vieja**
  - Dado `CLI-70001` en la versión `v`
  - Cuando se envía `If-Match: "v-1"`
  - Entonces responde `412` con `code=PRECONDITION_FAILED` y la versión sigue siendo `v`

Trazabilidad: `master-data-cache.feature` (stale If-Match), `versioning.spec.ts`,
`clients.e2e-spec.ts` (`should_return_400_for_malformed_if_match`,
`should_update_when_any_listed_entity_tag_matches`, `should_update_unconditionally_...`).

### REQ-CLI-005 Cambio sin efecto

Un `PATCH` cuyos valores ya coinciden con el cliente DEBE responder `200` con la entidad y el `ETag`
actuales, sin incrementar la versión ni escribir un evento de cambio.

- **Escenario: re-enviar el mismo estado**
  - Dado `CLI-70001` ya `WHOLESALE`
  - Cuando se envía `{"segment":"WHOLESALE"}`
  - Entonces la versión no cambia y el outbox no recibe nada

Trazabilidad: `clients.e2e-spec.ts`
(`should_answer_no_op_updates_with_the_current_entity_and_etag`), `mongo-persistence.e2e-spec.ts`
(`should_not_bump_the_version_or_write_events_for_no_op_updates`).

### REQ-CLI-006 Cambio y evento en la misma transacción

El cambio del cliente y la inserción del evento `clients.changed.v1` en `outbox` DEBEN ocurrir en la
misma transacción MongoDB de la base `clients`; si la escritura del outbox falla, el cambio DEBE
revertirse.

- **Escenario: outbox caído**
  - Dado un fallo al insertar en `outbox`
  - Cuando se actualiza un cliente
  - Entonces el cliente conserva su estado y versión anteriores

Trazabilidad: `mongo-persistence.e2e-spec.ts` (`should_update_and_write_the_contract_event_to_...`,
`should_roll_back_the_client_change_when_the_outbox_write_fails`).

### REQ-CLI-007 Evento de cambio sin datos personales

El evento DEBE llevar el estado completo necesario para elegibilidad (`eventId` UUIDv7,
`occurredAt`, `clientId`, `version`, `status`, `segment`, `taxRegime`, `market`) y NO DEBE llevar
`name` (el tópico compactado lo retendría indefinidamente).

- **Escenario: muestra de cambio**
  - Dado el bloqueo de `CLI-70001` en la versión 2
  - Cuando se publica el evento
  - Entonces contiene `status=BLOCKED`, `version=2`, `segment`, `taxRegime`, `market=MX` y no `name`

Trazabilidad: `client-changed.event.spec.ts`, `samples/changes/clients.changed.v1.json`,
`master-data-cache.feature`.

### REQ-CLI-008 Relay ordenado por clave

El relay DEBE reclamar, por cada `clientId`, sólo el evento no publicado más antiguo y sólo si es
reclamable (pendiente y disponible, o con lease vencido), con lease (`OUTBOX_LEASE_MS` 30 000) y
dueño; publicar con productor idempotente (key `clientId`, headers `eventId` y `contentType`);
marcar `PUBLISHED` sólo si sigue siendo dueño; ante fallo liberar con `attempts + 1` y
`availableAt = ahora + OUTBOX_RETRY_DELAY_MS` (1000). Los publicados DEBEN expirar a los
`OUTBOX_RETENTION` (7 d).

- **Escenario: dos versiones del mismo cliente**
  - Dado dos cambios pendientes de un cliente
  - Cuando corre el relay
  - Entonces se publica cada uno una vez, con key `clientId` y en orden de versión

Trazabilidad: `outbox-relay.e2e-spec.ts` (`should_publish_each_change_once_keyed_by_client_id_...`),
`mongo-persistence.e2e-spec.ts` (bloque `MongoOutboxStore`), `outbox-relay.spec.ts`.

### REQ-CLI-009 Semilla opcional que no pisa cambios

Con `SEED_ENABLED=true` la semilla DEBE insertarse con `$setOnInsert` (sólo lo que falta), de modo
que los cambios hechos por la API sobreviven a reinicios. Con `false` sólo se aseguran índices.

- **Escenario: re-siembra**
  - Dado `CLI-10003` bloqueado por la API
  - Cuando el servicio vuelve a sembrar
  - Entonces `CLI-10003` sigue bloqueado

Trazabilidad: `mongo-persistence.e2e-spec.ts` (`should_keep_changes_made_through_the_api_when_...`,
`should_only_ensure_indexes_when_seeding_is_disabled`).

### REQ-CLI-010 Límite de tasa por dirección y por principal

Toda ruta que no sea sonda DEBE pasar por dos token buckets (`RATE_LIMIT_RPS` 200,
`RATE_LIMIT_BURST` 400): uno por dirección antes de autenticar y otro por principal (`azp`, si no
`sub`) después. El exceso DEBE responder `429 RATE_LIMITED` con `Retry-After` en segundos. Las
sondas de health NO DEBEN limitarse. `X-Forwarded-For` sólo se usa con `TRUST_PROXY`.

- **Escenario: dos principales desde la misma dirección**
  - Dado un principal que agotó su bucket
  - Cuando otro principal llama desde la misma dirección
  - Entonces el segundo no es limitado

Trazabilidad: `resilience.e2e-spec.ts` (bloques de rate limiting), `token-bucket.spec.ts`,
`keyed-rate-limiter.spec.ts`.

### REQ-CLI-011 Inyección de fallos controlada

`FAULT_RULES` (`id:tipo[:veces]`, tipos `429`, `500`, `502`, `503`, `400`, `timeout`) DEBE aplicarse
sólo con `FAULT_INJECTION_ENABLED=true`. Con `veces`, falla las primeras N solicitudes de ese id y
luego responde normal; sin `veces`, falla siempre. `timeout` retiene `FAULT_TIMEOUT_MS` (5000) o
hasta que el cliente cancele.

- **Escenario: fixtures de resiliencia**
  - Dado `FAULT_RULES=CLI-40001:503:2,CLI-40002:503`
  - Cuando se consulta tres veces cada cliente
  - Entonces `CLI-40001` responde `503`, `503`, `200` y `CLI-40002` siempre `503`

Trazabilidad: `resilience.e2e-spec.ts` (`should_fail_first_two_requests_then_recover_when_rule_...`,
`should_always_fail_when_rule_has_no_times`, `should_send_retry_after_when_injecting_429`).

### REQ-CLI-012 Guardas de producción

Con `NODE_ENV=production` el servicio NO DEBE arrancar si `AUTH_ENABLED=false`,
`FAULT_INJECTION_ENABLED=true`, `API_DOCS_ENABLED=true`, `STORAGE_DRIVER=memory`,
`SEED_ENABLED=true`, `KAFKA_TLS_ENABLED=false` o sin `KAFKA_BOOTSTRAP_SERVERS`.

- **Escenario: autenticación apagada en producción**
  - Dado `NODE_ENV=production` y `AUTH_ENABLED=false`
  - Cuando se carga la configuración
  - Entonces el arranque falla

Trazabilidad: `load-config.spec.ts` (`should_refuse_unsafe_switches_in_production`,
`should_start_in_production_with_secure_defaults`).

### REQ-CLI-013 Salud y apagado controlado

`/health/live` DEBE responder `{"status":"UP"}`; `/health/ready` DEBE responder `503 DOWN` si falla
el ping a MongoDB o durante el apagado. Ante `SIGTERM` DEBE pasar a no listo, liberar retenciones de
fallos con `503`, terminar el lote del relay, esperar `SHUTDOWN_DRAIN_MS` (5000) y cerrar.

- **Escenario: base caída**
  - Dado MongoDB sin responder
  - Cuando se consulta `/health/ready`
  - Entonces responde `503` con `DOWN`

Trazabilidad: `platform.e2e-spec.ts` (`should_report_down_when_the_database_ping_fails`,
`should_drain_with_readiness_down_cancel_held_requests_and_close_connections`).

### REQ-CLI-014 Errores sin detalles internos

Un error inesperado DEBE responder `500 INTERNAL_ERROR` con un `detail` del catálogo de errores, sin
el mensaje de la excepción, y registrarse con su stack en el log.

- **Escenario: repositorio que falla**
  - Dado un repositorio que lanza un error inesperado
  - Cuando se consulta un cliente
  - Entonces responde `500` sin filtrar el mensaje original

Trazabilidad: `clients.e2e-spec.ts` (`should_return_500_internal_error_without_leaking_details`),
`problem-details.filter.spec.ts`.
