# Platform E2E (Karate)

Suite de aceptación que corre contra la plataforma levantada con `./mariposa.sh up`.
Publica eventos reales en Kafka, consulta la API de pedidos y lee `orders.processed.v1`,
`orders.processing.dlt`, `clients.changed.v1` y `products.changed.v1` para verificar efectos,
idempotencia, conflictos, versiones, resiliencia, catálogo de mercados e invalidación de caché.

```bash
./mariposa.sh e2e
# o solo Karate
cd e2e/karate && DEMO_PASSWORD=<DEMO_USER_PASSWORD> ./mvnw test
```

Reporte HTML: `target/karate-reports/karate-summary.html`.

## Catálogo de mercados (`markets.feature`, ADR 0006)

Reglas: descuento mayorista 3 % por línea con 20 unidades o más (cliente `WHOLESALE`); impuesto por categoría
del producto sobre el neto de la línea; **cada importe de línea se redondea HALF_UP a los decimales de la
moneda** y los totales son la suma de las líneas.

**Chile, CLP (0 decimales), `CLI-50001` WHOLESALE / GENERAL, IVA 19 % (REDUCED = STANDARD):**

| Línea | Bruto | Descuento | Neto | Impuesto |
|---|---|---|---|---|
| PRD-015 STANDARD, 24 × 1990 | 47760 | 47760 × 0,03 = 1432,8 → **1433** | 46327 | 46327 × 0,19 = 8802,13 → **8802** |
| PRD-016 REDUCED, 10 × 1290 | 12900 | 0 (menos de 20) | 12900 | 12900 × 0,19 = **2451** |
| **Total** | **60660** | **1433** | **59227** | **11253** → total **70480** |

**Ecuador, USD (2 decimales), `CLI-60001` WHOLESALE / GENERAL, IVA 15 %, reducido 5 %:**

| Línea | Bruto | Descuento | Neto | Impuesto |
|---|---|---|---|---|
| PRD-018 EXEMPT, 30 × 1.25 | 37.50 | 1.125 → **1.13** | 36.37 | 0.00 |
| PRD-019 REDUCED, 5 × 8.40 | 42.00 | 0 | 42.00 | 42.00 × 0,05 = **2.10** |
| **Total** | **79.50** | **1.13** | **78.37** | **2.10** → total **80.47** |

Un mercado fuera del catálogo (`AR`) termina en la DLT como `VALIDATION` y nunca se guarda como pedido.

## Invalidación de caché (`master-data-cache.feature`, ADR 0007)

Usa los datos semilla exclusivos `CLI-70001` y `PRD-020` (mercado MX). Cada escenario deja la entidad en
`ACTIVE` al empezar, así la suite se puede repetir aunque una corrida anterior haya fallado a mitad.

1. Lee la entidad como `admin` y guarda su `ETag` (`"<version>"`).
2. Procesa un pedido aprobado: la entidad queda en la caché de `order-processor`.
3. `PATCH` con `If-Match` a `BLOCKED` / `DISCONTINUED`; verifica la nueva versión y el evento de cambio en
   `clients.changed.v1` (key `clientId`) o `products.changed.v1` (key `market:productId`).
4. Publica pedidos nuevos hasta que uno termine `REJECTED` (`CLIENT_NOT_ACTIVE` / `PRODUCT_NOT_ACTIVE`), con un
   máximo de 5 intentos: la invalidación es asíncrona pero debe verse en segundos, no al vencer el TTL.
5. Reactiva la entidad y repite hasta ver `APPROVED`.

Además: un `If-Match` viejo responde `412 PRECONDITION_FAILED` sin cambiar la versión, y `analyst` recibe `403`
en ambos `PATCH`.

## Tasas de impuesto con vigencia (`tax-rates.feature`, ADR 0008)

Usa Ecuador, categoría `REDUCED`, con `PRD-019` (5 × 8.40 = 42.00 neto) y `CLI-60001`.

1. `analyst` recibe `403` al leer o proponer tasas: la API exige `orders-admin`.
2. Lee la tasa vigente (período aprobado abierto) y propone otra con `validFrom` **lejos en el futuro**
   (`TAX_RATE_LEAD_DAYS`, 50 años por defecto).
3. `admin` intenta aprobar su propia propuesta → `403 FOUR_EYES_REQUIRED`; `auditor` (sólo `orders-admin`) la
   aprueba → `200`, y el período anterior queda cerrado en `validFrom`.
4. Un pedido con `occurredAt` una hora después de `validFrom` usa la tasa nueva y trae
   `taxRateEffectiveFrom = validFrom`; uno un segundo antes usa la tasa anterior.
5. Una propuesta rechazada no se puede aprobar (`409 TAX_RATE_CONFLICT`), un id inexistente es `404` y un
   `validFrom` pasado es `400`.

Como cada corrida usa un `validFrom` posterior al de la anterior y todos los demás escenarios usan
`occurredAt` de 2026, la suite se puede repetir sin limpiar la colección y sin cambiar los totales esperados.
