# ADR 0007 — Eventos de cambio de datos maestros e invalidación de caché

- **Estado**: aceptado
- **Fecha**: 2026-09-23

## Contexto
`order-processor` consulta clientes y productos en cada pedido. Los productos se cacheaban 5 minutos sin aviso de
cambios (un producto recién descontinuado podía aprobarse) y los clientes no se cacheaban (un cliente bloqueado debe
rechazarse de inmediato). Las APIs usaban datos semilla en memoria y no tenían forma de modificar datos.

## Alternativas
1. Caché sólo por TTL: simple, pero con una ventana de datos viejos igual al TTL.
2. Consultar siempre sin caché: correcto, pero con más latencia y carga sobre las APIs.
3. Publicar desde las APIs sin outbox: un fallo entre guardar y publicar pierde el aviso y la caché queda vieja
   hasta el TTL.
4. **Cada API es dueña de su base, publica eventos de cambio con Transactional Outbox y `order-processor` invalida
   su caché al recibirlos; un TTL corto queda como red de seguridad.**

## Decisión
Opción 4.
- `clients-api` y `products-api` persisten en **su propia base de MongoDB** (`clients`, `products`), con usuario propio
  sin acceso a la base de pedidos. Los datos semilla se cargan al arrancar sólo si faltan, así los cambios hechos por
  la API sobreviven a reinicios.
- Nuevos endpoints de administración: `PATCH /clients/{clientId}` (rol `clients-admin`) y
  `PATCH /products/{productId}?market=` (rol `products-admin`), con control de concurrencia optimista por `version`
  (`If-Match` → `412` si no coincide) y respuesta con `ETag`.
- En la misma transacción que el cambio se inserta el evento en el outbox; un relay lo publica en
  `clients.changed.v1` / `products.changed.v1` (key = id, tópicos compactados), con el mismo esquema de lease que
  `order-processor`.
- **Los eventos de cambio nunca publican datos personales**: `clients.changed.v1` no lleva el nombre del cliente
  (un tópico compactado lo retendría indefinidamente, fuera del cifrado de `order-processor`). Quien necesite el
  nombre lo lee por HTTP a `clients-api`.
- `order-processor` cachea clientes (`CachingClientDirectory`) y productos en Redis con la **versión** de la entidad.
  Al recibir un evento de cambio **sobrescribe** la entrada si la versión es mayor (o la borra si el evento no trae el
  estado completo). Eventos viejos no pisan datos más nuevos.
- TTL de respaldo: clientes 60 s, productos 10 min (configurables). Si Redis falla, se consulta la API directo.

## Consecuencias
- Bloquear un cliente o descontinuar un producto se refleja en la caché en milisegundos, no en minutos.
- Queda una ventana igual a la latencia del evento. Es un riesgo aceptado y documentado.
- Dos bases y dos relays más que operar. Se mitiga con el mismo patrón, métricas y alertas en los tres servicios.
- Las APIs dejan de ser "sólo lectura": sus nuevos endpoints de escritura exigen roles de administración.

## Cuándo revisarla
Si el negocio exige ventana cero para clientes bloqueados, se mantiene la caché para el perfil y se consulta el
estado en línea. Si hay muchos consumidores de los cambios, se evalúa CDC (Debezium) en lugar de outbox propio.
