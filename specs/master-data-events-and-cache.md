# Eventos de datos maestros y caché (MDE)

## Propósito

Que bloquear un cliente o descontinuar un producto se refleje en la decisión del siguiente pedido en
milisegundos, sin consultar las APIs en cada pedido: `order-processor` cachea clientes y productos
en Redis con su versión y la actualiza con los eventos `clients.changed.v1` / `products.changed.v1`;
un TTL corto queda como red de seguridad (ADR 0007).

## Alcance

- Claves y formato de la caché, escritura condicionada por versión, lectura con relleno, listener
  de cambios, reintentos, degradación sin Redis, TTL y efecto observable en los pedidos.

## Fuera de alcance

- Cómo las APIs producen los eventos (`clients.md`, `products.md`), esquemas
  (`events-and-contracts.md`).

## Requisitos

### REQ-MDE-001 Claves y contenido de la caché

`order-processor` DEBE guardar clientes en `clients:{clientId}` y productos en
`products:{market}:{productId}` como hash con `v` (versión) y `d` (perfil JSON). El nombre del
cliente DEBE guardarse cifrado con la llave de PII. Sólo se cachean resultados encontrados; un `404`
NO DEBE cachearse.

- **Escenario: producto por mercado**
  - Dado `PRD-001` consultado para `MX`
  - Cuando se rellena la caché
  - Entonces la clave es `products:MX:PRD-001`

Trazabilidad: `CachingAdaptersTest` (`should_key_clients_by_id_and_fill_from_the_versioned_source`,
`should_key_products_by_market_and_id`),
`CacheCodecsTest.should_round_trip_clients_keeping_the_name_encrypted`,
`VersionedCacheTest.should_not_cache_not_found_results`.

### REQ-MDE-002 Escritura atómica "si es más nueva"

Toda escritura DEBE pasar por el script Lua `set-if-newer`: comparar versiones numéricamente, NO
reescribir una entrada con la misma versión y datos, conservar la versión de un borrado como piso,
aplicar el TTL en cada escritura efectiva y reemplazar entradas heredadas que no sean hash.

- **Escenario: comparación numérica**
  - Dado escrituras en versión `9` y luego `10`
  - Cuando llega otra escritura en versión `9`
  - Entonces no se aplica y la entrada sigue en `10` (no hay comparación lexicográfica)

Trazabilidad: `order-processor/.../infrastructure/cache/VersionedRedisStoreIT.java` (5 pruebas),
`VersionedRedisStoreTest`, `order-processor/src/main/resources/redis/set-if-newer.lua`.

### REQ-MDE-003 Lectura con relleno versionado

Ante un fallo de caché, el sistema DEBE consultar la API y escribir el resultado con la versión de
la entidad (campo `version`; si no, el `ETag` numérico, fuerte o débil; si no, `0`). Si la escritura
resulta obsoleta porque un evento ya dejó una versión mayor, DEBE devolverse la entrada más nueva de
la caché.

- **Escenario: lectura vieja de la API**
  - Dado `CLI-CACHEOLD1` cacheado en versión 4 y un evento que lo bloquea en versión 7
  - Cuando se intenta escribir una lectura de la API en versión 4 `ACTIVE`
  - Entonces la escritura es `STALE`, la caché sigue en 7 y el siguiente pedido es `REJECTED`
    con `CLIENT_NOT_ACTIVE`

Trazabilidad: `VersionedCacheTest.should_prefer_newer_cached_entry_when_the_fill_is_stale`,
`EntityVersionsTest`, `HttpAdaptersTest.should_read_client_version_from_body_before_etag`,
`MasterDataCacheIT.should_not_let_an_older_api_read_override_a_newer_cached_version`.

### REQ-MDE-004 Aplicación de eventos de cambio

Un listener en el grupo `order-processor-cache` DEBE consumir `clients.changed.v1` y
`products.changed.v1`: un evento con estado completo sobrescribe la entrada si su versión es mayor;
uno sin estado completo la borra dejando la versión como piso; uno con versión menor se descarta
(`stale`).

- **Escenario: bloquear y desbloquear un cliente cacheado**
  - Dado `CLI-70001` activo y cacheado por un pedido aprobado
  - Cuando `admin` lo bloquea por `PATCH` y se publica el evento
  - Entonces el siguiente pedido de `CLI-70001` es `REJECTED` con `CLIENT_NOT_ACTIVE`
  - Y tras reactivarlo el siguiente pedido es `APPROVED`
- **Escenario: evento viejo**
  - Dado la caché en una versión mayor
  - Cuando llega un evento con versión menor
  - Entonces se ignora

Trazabilidad: `master-data-cache.feature`,
`MasterDataCacheIT.should_reject_orders_of_a_cached_client_blocked_by_a_newer_event`,
`MasterDataCacheIT.should_ignore_change_events_older_than_the_cached_version`,
`MasterDataEventReaderTest`.

### REQ-MDE-005 Descontinuar un producto cacheado

Un evento `products.changed.v1` con `status=DISCONTINUED` DEBE hacer que el siguiente pedido con ese
producto en ese mercado se rechace, y la reactivación DEBE volver a aprobarlo.

- **Escenario: producto demo**
  - Dado `PRD-020` (MX) cacheado por un pedido de 2 × 10.0 aprobado
  - Cuando se descontinúa por `PATCH`
  - Entonces el siguiente pedido es `REJECTED` con `PRODUCT_NOT_ACTIVE`, y tras reactivarlo
    `APPROVED`

Trazabilidad: `master-data-cache.feature`,
`MasterDataCacheIT.should_reject_products_discontinued_by_a_change_event`.

### REQ-MDE-006 Eventos de cliente sin nombre

Como `clients.changed.v1` no trae `name`, el evento DEBE tratarse como estado completo para
elegibilidad conservando el nombre cifrado ya cacheado; si no hay entrada previa, DEBE dejarse sólo
la versión (borrado con piso) para que la siguiente lectura traiga el perfil completo de la API.

- **Escenario: sin nombre cacheado**
  - Dado que no hay entrada para el cliente
  - Cuando llega un evento sin `name`
  - Entonces la entrada queda sin datos y con la versión del evento

Trazabilidad: `CachingAdaptersTest` (`should_keep_the_cached_name_when_a_client_event_omits_it`,
`should_evict_when_a_nameless_client_event_has_no_cached_name`),
`MasterDataEventReaderTest.should_treat_client_events_without_name_as_full_state`.

### REQ-MDE-007 Eventos mal formados

Un evento ilegible o con `clientId`/`productId`, `version` (≥ 1), `market` o enums inválidos DEBE
registrarse, contarse como `ignored` y saltarse; NUNCA DEBE bloquear la partición ni afectar el
procesamiento de pedidos.

- **Escenario: JSON inválido**
  - Dado un registro con payload no JSON en `clients.changed.v1`
  - Cuando el listener lo lee
  - Entonces se cuenta `orders_cache_invalidations_total{outcome="ignored"}` y sigue con el
    siguiente

Trazabilidad: `MasterDataChangeListenerTest.should_skip_malformed_events_without_throwing`,
`MasterDataEventReaderTest.should_reject_unreadable_payloads`.

### REQ-MDE-008 Reintento acotado si Redis rechaza la escritura

Si Redis no acepta la escritura de un evento, el listener DEBE lanzar un error reintentable y el
contenedor DEBE reintentar con backoff exponencial (500 ms × 2, máximo 10 s, 6 intentos). Agotados,
DEBE contarse `error` una vez, registrarse y continuar; el TTL cubre la ventana.

- **Escenario: Redis caído durante un evento**
  - Dado Redis rechazando escrituras
  - Cuando llega un cambio de cliente
  - Entonces se reintenta 6 veces y luego se cuenta `outcome="error"`

Trazabilidad: `MasterDataErrorHandlersTest`,
`MasterDataChangeListenerTest.should_throw_a_retryable_failure_when_the_cache_write_fails`,
`CachingAdaptersTest.should_count_changes_dropped_after_retries`.

### REQ-MDE-009 Degradación sin Redis

Si Redis falla al leer o escribir durante un pedido, el sistema DEBE consultar la API directamente,
contar `orders_cache_errors_total{cache,operation}` y continuar el procesamiento. Una entrada
corrupta o incompleta DEBE tratarse como fallo de caché.

- **Escenario: Redis caído**
  - Dado Redis inaccesible
  - Cuando se procesa un pedido
  - Entonces el pedido se decide con datos de la API

Trazabilidad: `VersionedCacheTest` (`should_degrade_to_the_loader_when_redis_is_down`,
`should_treat_corrupted_and_incomplete_entries_as_misses`).

### REQ-MDE-010 TTL de respaldo

Cada entrada DEBE expirar a los `CACHE_CLIENTS_TTL` (60 s) o `CACHE_PRODUCTS_TTL` (10 min), que
DEBEN ser duraciones positivas; un valor cero o negativo DEBE impedir el arranque. La caché de cada
tipo puede apagarse con `CACHE_CLIENTS_ENABLED` / `CACHE_PRODUCTS_ENABLED`.

- **Escenario: TTL inválido**
  - Dado `CACHE_CLIENTS_TTL=0s`
  - Cuando arranca
  - Entonces falla la validación

Trazabilidad: `CachePropertiesTest` (`should_reject_non_positive_ttls`,
`should_accept_positive_ttls`), `VersionedRedisStoreIT.should_set_the_ttl_on_every_applied_write`.
