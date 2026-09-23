# Seguridad (SEC)

## Propósito

Proteger pedidos, catálogos, clientes y tasas con OAuth2/OIDC de Keycloak (ADR 0003): cada API
valida JWT con audiencia y rol de realm, los servicios se autentican entre sí con client
credentials, la PWA usa PKCE, los datos personales se cifran y ningún detalle interno o secreto sale
del proceso.

## Alcance

- Validación de tokens, matriz de roles, desactivación de la autenticación, credenciales entre
  servicios, cifrado de PII, saneamiento de causas, errores sin detalles, secretos, endpoints
  públicos, realm local y red en EKS.

## Fuera de alcance

- Flujo OIDC de la PWA en detalle (`order-tracker.md`), límites de tasa de las APIs
  (`clients.md`, `products.md`).

## Requisitos

### REQ-SEC-001 Validación del JWT con audiencia obligatoria

`order-processor`, `products-api` y `clients-api` DEBEN validar firma (JWKS de Keycloak), emisor
(`AUTH_ISSUER`), expiración y audiencia (`order-processor`, `products-api`, `clients-api`). Sin
token o con token inválido (firma, `iss`, `aud`, `exp`, `alg`, `kid` desconocido) DEBEN responder
`401 UNAUTHORIZED` con `WWW-Authenticate: Bearer`. Con autenticación activa, un servicio sin
audiencia configurada NO DEBE arrancar.

- **Escenario: token para otra audiencia**
  - Dado un token válido emitido para otra audiencia
  - Cuando llama a `clients-api`
  - Entonces responde `401`
- **Escenario: arranque sin audiencia**
  - Dado `AUTH_ENABLED=true` y `AUTH_AUDIENCE` vacío en `order-processor`
  - Cuando arranca
  - Entonces falla con "A JWT audience (AUTH_AUDIENCE) is required"

Trazabilidad: `WebSupportTest.should_fail_closed_without_audience_while_authentication_is_enabled`,
`OrdersApiIT.should_authorize_real_keycloak_style_tokens_by_realm_role_and_audience`,
`verifier_test.go` (`TestVerifyAlwaysChecksAudience`, `TestVerifyRejectsInvalidTokens`),
`guards_test.go` (`TestUnauthorizedIncludesChallenge`), `clients.e2e-spec.ts`
(`should_return_401_when_token_targets_another_audience`, `..._is_expired`, `..._another_issuer`).

### REQ-SEC-002 Matriz de roles por endpoint

Los roles de realm (`realm_access.roles`) DEBEN autorizar así; un token válido sin el rol DEBE
recibir `403 FORBIDDEN`:

| Endpoint | Rol |
|---|---|
| `GET /orders`, `GET /orders/{orderId}` | `orders-reader` u `orders-admin` |
| `GET` / `POST /tax-rates`, `POST /tax-rates/{id}/approve` y `/reject` | `orders-admin` |
| `/actuator/**` de `order-processor` (salvo health y prometheus) | `orders-admin` |
| `GET /products/{productId}` / `PATCH` | `products-reader` / `products-admin` |
| `GET /clients/{clientId}` / `PATCH` | `clients-reader` / `clients-admin` |

- **Escenario: usuario sin roles**
  - Dado `viewer` (sin roles)
  - Cuando llama a `GET /orders` o a `GET /clients/CLI-99821`
  - Entonces recibe `403`
- **Escenario: analista intentando administrar**
  - Dado `analyst` (sólo `orders-reader`)
  - Cuando envía `PATCH` a `CLI-70001` o a `PRD-020`, o lee `PRD-001`
  - Entonces recibe `403`; y un `orders-reader` en `GET /tax-rates` recibe `403 FORBIDDEN`

Trazabilidad: `orders-api.feature`, `clients-api.feature`, `products-api.feature`,
`master-data-cache.feature` (admin roles), `tax-rates.feature` (only orders-admin),
`OrdersControllerTest`, `TaxRatesApiIT`,
`update_test.go` (`TestPatchRequiresAdminRole`), `clients.e2e-spec.ts`
(`should_return_403_for_readers_without_the_admin_role`).

### REQ-SEC-003 JWKS no disponible es transitorio

Si el JWKS no puede descargarse (o el contexto se cancela), `products-api` DEBE responder
`503 SERVICE_UNAVAILABLE` para que el llamador reintente, y su readiness DEBE quedar `DOWN` hasta
cargar al menos una llave utilizable.

- **Escenario: Keycloak caído**
  - Dado el JWKS inaccesible
  - Cuando llega una solicitud con token
  - Entonces responde `503`

Trazabilidad: `guards_test.go` (`TestJWKSDownReturns503`), `verifier_test.go`
(`TestVerifyReportsUnavailableJWKS`, `TestReadinessStaysDownWithEmptyKeySet`).

### REQ-SEC-004 Apagar la autenticación sólo en local

`order-processor` DEBE rechazar `AUTH_ENABLED=false` salvo con el perfil `local`. `clients-api` DEBE
rechazarlo con `NODE_ENV=production`. Con autenticación activa y sin issuer o JWKS, `clients-api` y
`products-api` NO DEBEN arrancar.

- **Escenario: seguridad apagada fuera de local**
  - Dado `AUTH_ENABLED=false` sin perfil `local`
  - Cuando arranca `order-processor`
  - Entonces falla con "Authentication can only be disabled with the 'local' profile"

Trazabilidad: `WebSupportTest.should_only_allow_disabled_security_in_local_profile`,
`OpenSecurityTest`, `load-config.spec.ts` (`should_fail_closed_when_auth_is_enabled_without_...`),
`config_test.go` (`TestLoadRequiresAuthSettingsWhenEnabled`).

### REQ-SEC-005 Credenciales entre servicios

`order-processor` DEBE llamar a las APIs con un token de client credentials del cliente
`order-processor` (roles `products-reader` y `clients-reader`, audiencias `products-api` y
`clients-api`), con el secreto sólo en `OAUTH_CLIENT_SECRET`. Un fallo al obtener el token DEBE
tratarse como `EXTERNAL_TRANSIENT`.

- **Escenario: Keycloak no entrega token**
  - Dado el endpoint de token inaccesible
  - Cuando se consulta un cliente
  - Entonces el fallo es transitorio y se reintenta

Trazabilidad: `LookupExchangeTest.should_treat_token_acquisition_failures_as_transient`,
`infra/keycloak/realm-mariposa.json` (cliente `order-processor`).

### REQ-SEC-006 Cifrado del nombre del cliente

El nombre del cliente DEBE guardarse en MongoDB y en Redis cifrado con AES-256-GCM (IV aleatorio de
12 bytes, tag de 128 bits, id de llave como prefijo y como AAD). `PII_ENCRYPTION_KEY` (32 bytes en
base64) es obligatoria; `PII_PREVIOUS_ENCRYPTION_KEY` / `PII_PREVIOUS_KEY_ID` permiten leer datos de
la llave anterior durante una rotación. Un texto alterado, mal formado o de llave desconocida DEBE
fallar. La API DEBE devolver el nombre descifrado sólo a `orders-reader` / `orders-admin`.

- **Escenario: rotación de llave**
  - Dado un nombre cifrado con la llave `k1`
  - Cuando la llave activa pasa a `k2` con `k1` como anterior
  - Entonces el nombre cifrado con `k1` se descifra y los nuevos cifrados empiezan con `k2:`
- **Escenario: nombre en la API**
  - Dado `ORD-MX-000147` guardado
  - Cuando `analyst` lo consulta
  - Entonces `client.name` es `Distribuidora Central`

Trazabilidad: `AesGcmPiiCipherTest`,
`OrderDocumentMapperTest.should_store_money_as_decimal128_and_encrypt_client_name`,
`CacheCodecsTest`, `order-processing.feature` (golden example),
`OrdersApiIT.should_return_order_matching_openapi_contract_with_decrypted_name`.

### REQ-SEC-007 Saneamiento de causas e identificadores

Toda causa de error que salga del proceso (header `x-error-cause`, `failure.cause`, logs) DEBE
reemplazar caracteres de control, enmascarar `Bearer …`, pares `password|secret|token|api_key = …` y
correos con `[redacted]`, y truncarse a 256 caracteres. Los identificadores en headers DEBEN
limitarse a `[A-Za-z0-9._:-]` y 64 caracteres.

- **Escenario: causa con secretos**
  - Dado el texto "line1⏎line2⇥Bearer abc.def.ghi token=xyz mail a@b.com" (⏎ salto, ⇥ tab)
  - Cuando se sanea
  - Entonces queda `line1 line2 [redacted] [redacted] mail [redacted]`

Trazabilidad: `DltSupportTest` (`should_strip_control_characters_secrets_and_emails`,
`should_describe_failures_without_leaking_secrets`, `should_truncate_causes_and_identifiers`).

### REQ-SEC-008 Errores sin detalles internos

Un error inesperado DEBE responder `500 INTERNAL_ERROR` con un `detail` genérico y registrarse del
lado del servidor; un almacén caído en `order-processor` DEBE responder `503 SERVICE_UNAVAILABLE`.
NUNCA DEBE devolverse `4xx` por un error inesperado ni exponerse mensajes o stacktraces.

- **Escenario: excepción no controlada**
  - Dado un fallo inesperado en la consulta
  - Cuando se llama a `GET /orders/{id}`
  - Entonces responde `500` con "Unexpected error while processing the request"

Trazabilidad: `OrdersControllerTest` (`should_hide_unexpected_errors_behind_500`,
`should_answer_503_when_store_is_unavailable`), `products_test.go`
(`TestPanicIsRecoveredAsInternalError`), `clients.e2e-spec.ts`
(`should_return_500_internal_error_without_leaking_details`).

### REQ-SEC-009 Secretos sólo por entorno

Los secretos de `order-processor` (`PII_ENCRYPTION_KEY`, `OAUTH_CLIENT_SECRET`, contraseñas en
`MONGODB_URI`, `SPRING_DATA_REDIS_PASSWORD`) DEBEN leerse sólo del entorno y NO DEBEN tener valor
por defecto en `application.yml`. `./mariposa.sh init` DEBE generar los secretos locales al azar y
la contraseña demo sólo se muestra con `urls --show-secrets`.

- **Escenario: llave ausente**
  - Dado el entorno sin `PII_ENCRYPTION_KEY`
  - Cuando se resuelve el secreto
  - Entonces falla con "Secret PII_ENCRYPTION_KEY must be provided through the environment"

Trazabilidad: `ConfigurationSupportTest.should_read_secrets_only_from_the_environment`,
`.env.example`, `mariposa.sh` (`fill_empty_secrets`).

### REQ-SEC-010 Endpoints públicos acotados

Sólo health (`/health/**`, `/livez`, `/readyz`, `/actuator/health/**`), métricas
(`/actuator/prometheus`, `/metrics`) y, si `API_DOCS_ENABLED=true`, Swagger DEBEN quedar sin
autenticación; se asumen accesibles sólo dentro de la red de la plataforma y NO DEBEN publicarse por
un Ingress. Con `API_DOCS_ENABLED=false` Swagger NO DEBE servirse.

- **Escenario: documentación apagada**
  - Dado `API_DOCS_ENABLED=false`
  - Cuando se pide `/swagger-ui.html` o `/docs`
  - Entonces no se sirve

Trazabilidad: `OrdersApiIT.should_expose_probes_and_metrics_for_internal_scraping`,
`OrdersControllerTest.should_hide_api_docs_when_disabled`, `guards_test.go`
(`TestHealthAndMetricsSkipAuthentication`), `platform.e2e-spec.ts`
(`should_not_serve_swagger_when_api_docs_are_disabled`).

### REQ-SEC-011 Entrada validada con listas blancas

Todo identificador recibido por HTTP DEBE validarse contra un patrón antes de usarse en una consulta
(sin concatenación ni inyección NoSQL): `orderId` con `^[A-Za-z0-9][A-Za-z0-9._:-]{0,63}$`,
`clientId` y `productId` con sus patrones de contrato.

- **Escenario: intento de inyección**
  - Dado `GET /orders/{"$gt":""}`
  - Cuando se procesa
  - Entonces responde `400 VALIDATION_ERROR` con `errors[0].field = orderId`

Trazabilidad: `OrdersControllerTest.should_reject_order_ids_that_could_be_injections`,
`clients-api.feature` (malformed id), `products-api.feature` (malformed id).

### REQ-SEC-012 Identidad del actor en la administración de tasas

El actor de una propuesta o revisión de tasa DEBE ser el claim `preferred_username` del token; si
falta, el `sub`. Sin autenticación JWT se usa el nombre de la autenticación (o `anonymous`).

- **Escenario: token sin nombre preferido**
  - Dado un JWT sin `preferred_username` con `sub=sub-1`
  - Cuando se resuelve el actor
  - Entonces es `sub-1`; sin autenticación es `anonymous`

Trazabilidad: `TaxRateWebMapperTest.should_resolve_actors_from_jwt_or_authentication_name`.

### REQ-SEC-013 Realm local de demostración

El realm `mariposa` de `infra/keycloak/realm-mariposa.json` DEBE existir sólo en local, con roles
`orders-reader`, `orders-admin`, `products-reader`, `products-admin`, `clients-reader`,
`clients-admin`; usuarios `analyst` (`orders-reader`), `admin` (los seis), `auditor` (sólo
`orders-admin`, segundo par de ojos para aprobar tasas) y `viewer` (ninguno), con contraseña
`DEMO_USER_PASSWORD`; cliente `orders-cli` (password grant, tokens de 900 s) sólo para
herramientas; tokens de acceso de 300 s, rotación de refresh tokens y protección de fuerza bruta
(`failureFactor` 10). En staging y producción los usuarios vienen del IdP corporativo.

- **Escenario: tokens de la suite**
  - Dado Karate pidiendo un token por usuario con `callSingle`
  - Cuando `viewer` llama a `GET /orders`
  - Entonces recibe `403`

Trazabilidad: `e2e/karate/.../common/tokens.feature`, `orders-api.feature`, `tax-rates.feature`
(`auditor` aprueba lo que propone `admin`); configuración del realm sin prueba propia.

### REQ-SEC-014 Red y exposición en EKS

En EKS cada servicio DEBE aceptar tráfico sólo de sus consumidores y del namespace de monitoreo:
`order-processor` desde `order-tracker`; las APIs desde `order-processor` y, para los `PATCH`, desde
pods `admin-tools` o el namespace `operations`. Sólo `order-tracker` tiene Ingress (ALB, HTTPS) y el
chart DEBE fallar si un servicio con Ingress activa la NetworkPolicy sin `allowedCidrs`.

- **Escenario: Ingress sin CIDR**
  - Dado `ingress.enabled=true` y NetworkPolicy sin `allowedCidrs`
  - Cuando se renderiza el chart
  - Entonces falla con "networkPolicy.allowedCidrs must list the VPC CIDR when ingress is enabled"

Trazabilidad: ⚠️ sin test (`scripts/ci/validate-helm.sh` sólo renderiza los values válidos).

### REQ-SEC-015 Config server protegido

`config-server` DEBE exigir autenticación básica (`CONFIG_SERVER_USERNAME` /
`CONFIG_SERVER_PASSWORD`) para servir configuración y para `/actuator/prometheus`; health queda
público.

- **Escenario: sin credenciales**
  - Dado una petición sin credenciales
  - Cuando pide `/sample-service/docker`
  - Entonces responde `401`

Trazabilidad: `config-server/src/test/java/.../ConfigServerApplicationTest.java` (4 pruebas).
