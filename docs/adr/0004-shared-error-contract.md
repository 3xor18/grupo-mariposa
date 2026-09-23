# ADR 0004 — Contrato de error compartido (RFC 9457)

- **Estado**: aceptado
- **Fecha**: 2026-09-22

## Contexto
Tres servicios en tres lenguajes deben responder los errores de forma homogénea, para que `order-processor`
clasifique los fallos sin conocer detalles internos de cada proveedor.

## Decisión
`application/problem+json` (RFC 9457) con las extensiones `code` (estable, para máquinas), `traceId` y
`timestamp`. El código HTTP define la clase de error (`404` inexistente, `429`/`5xx` transitorio, `400`
definitivo) y `code` define el motivo. El esquema vive en `contracts/common/problem.schema.json` y cada servicio
lo valida en sus tests.

## Consecuencias
Se comparte un **contrato**, no una librería: cada equipo lo implementa en su lenguaje sin acoplarse por versiones.
El consumidor nunca parsea `detail` (texto para humanos) para decidir.

## Cuándo revisarla
Si se adopta un API gateway que normalice los errores.
