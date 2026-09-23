# config-repo

Repositorio de configuración centralizado servido por `config-server` (Spring Cloud Config) a los cuatro
componentes: `order-processor` (cliente Spring nativo) y `products-api`, `clients-api` y `order-tracker`
(leen `/{app}-{perfil}.properties` al arrancar).

| Archivo | Se aplica a |
|---|---|
| `application.yml` | todos los servicios: catálogo de mercados y monedas (`platform.*`, ADR 0006) |
| `application-docker.yml` | todos los clientes Spring con perfil `docker` |
| `<servicio>.yml` | valores por defecto del servicio |
| `<servicio>-docker.yml` | Compose local (inyección de fallos activa, tópicos de réplica 1, trazas al 100 %) |
| `<servicio>-staging.yml` | EKS staging (`CONFIG_PROFILE` / `SPRING_PROFILES_ACTIVE=staging`) |
| `<servicio>-production.yml` | EKS producción (`CONFIG_PROFILE` / `SPRING_PROFILES_ACTIVE=production`) |

Reglas:

- **Nunca** contiene secretos. Los secretos llegan por variables de entorno desde `.env` (local),
  GitHub Secrets (CI) o AWS Secrets Manager vía External Secrets (EKS).
- **Nunca** contiene hosts de un ambiente. Los endpoints de EKS (Keycloak, MSK, ElastiCache, dominios públicos)
  viven en `deploy/helm/values/<ambiente>/<servicio>.yaml`; aquí sólo hay parámetros de negocio y técnicos.
- Precedencia en todos los servicios: **variable de entorno > config server > valor por defecto**. Una variable
  definida en los values de Helm oculta el valor de este directorio.
- Las tasas `PRICING_TAX_*` son sólo la **semilla y el respaldo** de la colección `tax_rates` (ADR 0008): se
  copian a MongoDB la primera vez que falta una combinación mercado/categoría y se usan si la colección nunca
  pudo cargarse. Un cambio de tasa con fecha se hace con la API `/tax-rates` (propuesta + aprobación de otra
  persona), no con un PR aquí.
- Cambiar un parámetro de negocio (descuento) o técnico (timeouts, reintentos, TTL de caché) es
  un PR sobre este directorio, revisado y auditable, sin recompilar el servicio. Aplica a los mercados existentes:
  agregar un país todavía requiere código (TODO-1 de `docs/roadmap.md`). Se aplica con un reinicio
  progresivo (`kubectl rollout restart`) porque la configuración es inmutable en tiempo de ejecución.
- En EKS el servidor usa el backend `git` sobre este mismo repositorio (`CONFIG_GIT_URI`, rama `main`,
  `search-paths: config-repo`) con credenciales `CONFIG_GIT_USERNAME` / `CONFIG_GIT_PASSWORD` desde
  External Secrets. Alternativa: `CONFIG_BACKEND=awsparamstore` con prefijo `/grupo-mariposa`.
- La inyección de fallos (`fault.injection-enabled`) sólo está activa en el perfil `docker`; staging y producción
  la desactivan aquí y además con `FAULT_INJECTION_ENABLED=false` en Helm.
