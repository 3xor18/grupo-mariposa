# ADR 0005 — Librería estándar para el transporte HTTP de products-api

- **Estado**: aceptado
- **Fecha**: 2026-09-22

## Contexto
`products-api` expone un endpoint de lectura y un healthcheck. El PDF pide justificar el uso o no de un framework.

## Decisión
`net/http` con el enrutador de Go 1.22 (`GET /products/{productId}`), middlewares como funciones
`func(http.Handler) http.Handler` y `log/slog` para logs JSON. Sólo se usan dependencias externas donde no hay
equivalente estándar: validación de JWT y cliente de Prometheus.

## Consecuencias
Menos superficie de dependencias y de CVEs, código idiomático y fácil de testear con `httptest`. El recovery y el
request-id se escriben a mano (pocas líneas).

## Cuándo revisarla
Si el servicio crece a decenas de rutas con validación declarativa o negociación de contenido compleja.
