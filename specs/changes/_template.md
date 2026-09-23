# Cambio: <título corto>

- **Fecha**: <yyyy-mm-dd>
- **Estado**: propuesto | aprobado | implementado | archivado
- **Autor**: <usuario>
- **Aprobado por**: <usuario> (<fecha>)
- **Specs afectadas**: `<capacidad>.md` → REQ-<AREA>-NNN, …

## Motivación

<2-4 líneas: qué problema de negocio o técnico resuelve y por qué ahora.>

## Contraste con la constitución

- <CON-xx>: cumple | enmienda propuesta (<motivo>)

## Decisiones tomadas

1. <pregunta resuelta → decisión>

## ADDED Requirements

### REQ-<AREA>-NNN <título>

<Enunciado normativo con DEBE / NO DEBE.>

- **Escenario: <nombre>**
  - Dado <contexto con valores concretos>
  - Cuando <acción>
  - Entonces <resultado observable>

Trazabilidad: <test que se escribirá: clase/método, feature Karate con tag `@REQ-<AREA>-NNN`>

## MODIFIED Requirements

### REQ-<AREA>-NNN <título>

- Antes: <enunciado vigente>
- Después: <enunciado nuevo>
- Escenarios nuevos o cambiados: <…>

## REMOVED Requirements

- REQ-<AREA>-NNN: <motivo y plan de retiro>

## Preguntas abiertas

1. <pregunta> — Recomendación: <opción y costo>

## Impacto

| Dimensión | Detalle |
|---|---|
| Servicios | <order-processor, …> |
| Contratos | <archivo en `contracts/`; aditivo o `.v2`> |
| Eventos | <tópicos> |
| config-repo | <claves nuevas o cambiadas> |
| Datos | <colecciones, índices, migración> |
| Seguridad | <roles, audiencias, PII> |
| Observabilidad | <métricas, alertas, dashboards> |
| ADR | <nuevo ADR si es decisión arquitectónica> |

## Plan de pruebas

| REQ | Nivel | Prueba |
|---|---|---|
| REQ-<AREA>-NNN | unit / IT / Karate / Playwright | <nombre> |
