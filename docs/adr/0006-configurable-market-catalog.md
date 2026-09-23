# ADR 0006 — Catálogo de mercados configurable

- **Estado**: aceptado (reemplaza el `enum` de mercados de la primera versión)
- **Fecha**: 2026-09-23

## Contexto
La primera versión modelaba MX, CO y PE como `enum` en los cuatro servicios y en los contratos. El negocio sumará más
países de Latinoamérica. Con enums, cada país nuevo exigía cambiar y desplegar cuatro servicios y un cambio de
contrato. Además, dos supuestos implícitos no se sostienen fuera de los tres primeros mercados:
- **Todas las monedas tienen dos decimales**: falso para el peso chileno (CLP, 0 decimales).
- **Cada mercado tiene su propia moneda**: falso para Ecuador, que usa dólares (USD), igual que otros países.

## Alternativas
1. Mantener los enums y agregar cada país con código: simple, pero lento y con riesgo de desincronizar servicios.
2. Un microservicio de "referencia" que expone mercados y monedas por HTTP: una dependencia síncrona más en el camino
   crítico de cada pedido.
3. **Catálogo compartido en `config-repo`** (`application.yml`, servido a todos los servicios por el config server),
   validado al arrancar.

## Decisión
Opción 3.
- `platform.markets` define por mercado su **moneda** y **locale**: `MX:MXN:es-MX,CO:COP:es-CO,…`.
- `platform.currencies` define por moneda sus **decimales** (ISO 4217 minor units): `MXN:2,COP:2,PEN:2,CLP:0,USD:2`.
- En el código, el mercado es un *value object* validado contra el catálogo (`MarketCode`, dos letras mayúsculas), no
  un `enum`. El mismo formato en Java, Go, TypeScript y Dart.
- `order-processor` exige además la tabla de impuestos completa (tres categorías) para **cada** mercado del catálogo;
  si falta algo, no arranca.
- Todos los importes se redondean con HALF_UP **a los decimales de la moneda** del pedido (CLP a enteros).
- Contratos: `market` y `currency` pasan de `enum` a `pattern` (`^[A-Z]{2}$`, `^[A-Z]{3}$`). Es un cambio que
  ensancha lo aceptado: los productores no se rompen y los consumidores ya eran *tolerant readers*.
- La PWA recibe la lista de mercados desde `config.json` (generado desde el config server).

Mercados habilitados tras la decisión: MX, CO, PE, **CL** (CLP, 0 decimales; Chile no tiene IVA reducido, por lo que
`REDUCED` se configura igual a `STANDARD`) y **EC** (USD compartido; IVA 15 %, reducido 5 %).

## Consecuencias
- Un país nuevo es un PR a `config-repo` (catálogo + impuestos) y datos de productos y clientes, sin código.
- Un mercado fuera del catálogo sigue siendo `VALIDATION` → DLT, nunca un cálculo con impuesto inventado.
- El catálogo es una decisión transversal: se cambia con revisión de los dueños de los cuatro servicios.

## Cuándo revisarla
Si los mercados necesitan reglas propias (impuestos por región, redondeo legal distinto a HALF_UP, varias monedas por
mercado) o si un área de negocio necesita editarlos sin PR: moverlo a un servicio de *pricing* con persistencia
(ver TODO-2 del roadmap).
