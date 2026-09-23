# Estándares de ingeniería

Quality gates que el CI hace cumplir y que el code review verifica. Aplican a los cuatro componentes.

## Código
- Nombres que expresan intención. **Sin comentarios en el código**: si algo necesita explicación, se renombra o se
  extrae. La documentación vive en `docs/`, en los ADRs y en OpenAPI.
- **Sin literales mágicos**: los strings y números con significado de negocio o de configuración van en constantes,
  enums o propiedades tipadas.
- Líneas de **100 caracteres como máximo**. Funciones cortas (idealmente ≤ 20 líneas) con un solo nivel de abstracción.
- Inmutabilidad por defecto (`record`/`final`, `readonly`, valores en Go). Sin estado global mutable.
- DRY sin sobre-abstraer: se extrae cuando hay una segunda repetición real, no por anticipación.
- Los patrones de diseño se usan sólo cuando resuelven un problema concreto (Strategy para impuestos por mercado,
  Decorator para la caché, Ports & Adapters para las fuentes de datos, Factory para los errores).
- Manejo de errores explícito: nunca se tragan excepciones y nunca se devuelve 4xx para un error inesperado.
- Seguridad: validación de toda entrada, sin concatenación de queries (sin inyección NoSQL), sin secretos en el
  código ni en los logs, PII cifrada en reposo.

## Pruebas
- **100 % de líneas** en dominio y aplicación; ≥ 90 % global por servicio. El build falla bajo el umbral.
- Flutter: la VM de Dart no siempre reporta las declaraciones de constructores `const` (se evalúan en
  compilación), así que el gate de CI no las cuenta como faltantes; cualquier otra línea sin cubrir lo rompe.
- Las pruebas documentan comportamiento: nombres `should_<resultado>_when_<condición>` o equivalente idiomático.
- Además del happy path, siempre: bordes, errores, duplicados y concurrencia donde aplique.

## Git
- Conventional Commits (`feat`, `fix`, `test`, `refactor`, `docs`, `build`, `ci`, `chore`), un cambio lógico por commit.
- Ramas: `main` (productivo) ← `develop` (integración) ← `feature/<servicio-o-tema>`. Merge con `--no-ff` y PR revisado.

## Contenedores
- Dockerfile multi-stage, imagen final mínima, **usuario no root**, `HEALTHCHECK`, sin secretos en capas.
- Toda configuración entra por variables de entorno (12-factor).
