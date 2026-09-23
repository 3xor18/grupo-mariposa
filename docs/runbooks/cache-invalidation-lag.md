# Runbook — "Bloqueé un cliente (o descontinué un producto) y siguen aprobándose pedidos"

Alertas relacionadas: `MasterDataOutboxAging`, `CacheInvalidationFailures`.
Diseño: [ADR 0007](../adr/0007-master-data-change-events-and-cache.md).

El camino del cambio es: `PATCH` en la API → documento + outbox en la **misma transacción** (base `clients` o
`products`) → relay → `clients.changed.v1` / `products.changed.v1` → listener de `order-processor` (grupo
`order-processor-cache`) → entrada de Redis con la versión nueva. Si algo se corta, la caché vieja se sirve hasta
el TTL de respaldo (`CACHE_CLIENTS_TTL` 60 s, `CACHE_PRODUCTS_TTL` 10 min).

## 0. Datos que hay que pedir
`clientId` (o `productId` y `market`), hora del cambio, `orderId` del pedido que se aprobó de más.

## 1. ¿El cambio se guardó?
```bash
curl -s -H "Authorization: Bearer $(./mariposa.sh token admin)" localhost:8082/clients/CLI-70001 -i
```
- El `ETag` debe ser la versión nueva y `status` el valor esperado. Si no, el `PATCH` falló (`412` por un
  `If-Match` viejo, `403` sin rol `clients-admin` / `products-admin`): no es un problema de caché.
- En EKS el `PATCH` sólo llega desde pods `admin-tools` o el namespace `operations` (NetworkPolicy); un timeout
  desde otro origen es la política, no la API.

## 2. ¿El outbox de la API está publicando?
- Panel **"Outbox de clients-api y products-api"**: antigüedad y pendientes por servicio. Una antigüedad creciente
  es el relay detenido (Kafka caído, lease tomado por un pod muerto hasta que vence `OUTBOX_LEASE_MS`).
- Logs de la API filtrados por el id; en local `./mariposa.sh logs clients-api`.

## 3. ¿El evento está en el tópico?
Kafka UI → `clients.changed.v1` (key `clientId`) o `products.changed.v1` (key `market:productId`); o
`./mariposa.sh consume clients.changed.v1`. Debe existir un mensaje con la `version` del paso 1.
- **No está** → volver al paso 2.
- **Está** → paso 4.

## 4. ¿order-processor lo aplicó?
- Panel **"Invalidaciones de caché por evento de cambio"**: `outcome="applied"` sube con cada cambio;
  `outcome="stale"` significa que la caché ya tenía una versión igual o mayor (normal si el evento llegó dos veces);
  `outcome="ignored"` es un evento que no aplica a la caché; `outcome="failed"` / `"error"` disparan la alerta
  `CacheInvalidationFailures` (Redis inaccesible o script de versión fallido).
- Lag del grupo `order-processor-cache` en Kafka UI. Lag creciente = listener detenido o en error.
- En local, la entrada de Redis:
  ```bash
  docker compose exec redis redis-cli --no-auth-warning HGETALL clients:CLI-70001
  docker compose exec redis redis-cli --no-auth-warning HGETALL products:MX:PRD-020
  ```
  Las entradas son hashes con la entidad y su `version` (la contraseña llega por `REDISCLI_AUTH` en el
  contenedor). Deben tener la versión nueva o no existir.

## 5. Mitigación
- Borrar la entrada (`redis-cli DEL clients:<id>` o `DEL products:<market>:<id>`): el siguiente pedido lee la API.
- Si Redis está caído, `order-processor` ya consulta la API directo; no hace falta intervenir la caché.
- Los pedidos aprobados de más dentro de la ventana se revisan con negocio; no se reprocesan automáticamente.

## 6. Cierre
Registrar la ventana real (hora del `PATCH` contra hora del primer pedido rechazado) y la causa. Una ventana mayor al
TTL indica que también falló la red de seguridad y merece un postmortem.
