# config-repo

Repositorio de configuración centralizado servido por `config-server` (Spring Cloud Config).

| Archivo | Se aplica a |
|---|---|
| `application.yml` | todos los servicios Spring |
| `<servicio>.yml` | valores por defecto del servicio |
| `<servicio>-<perfil>.yml` | ambiente: `docker` (local), `staging`, `production` |

Reglas:

- **Nunca** contiene secretos. Los secretos llegan por variables de entorno desde `.env` (local),
  GitHub Secrets (CI) o AWS Secrets Manager vía External Secrets (EKS).
- Cambiar un parámetro de negocio (tasas de impuesto, descuento) o técnico (timeouts, reintentos,
  TTL de caché) es un PR sobre este directorio, revisado y auditable, sin recompilar el servicio.
- En AWS el mismo servidor puede usar el backend `git` (este repo) o `awsparamstore`
  (`CONFIG_BACKEND=awsparamstore`, prefijo `/grupo-mariposa`).
- `products-api` y `clients-api` siguen 12-factor puro: se configuran por variables de entorno
  (Compose en local, `deploy/helm/values/*.yaml` por ambiente en Kubernetes).
