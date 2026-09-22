# ADR 0003 — Autenticación y autorización con Keycloak

- **Estado**: aceptado
- **Fecha**: 2026-09-22

## Contexto
El PDF no exige autenticación, pero una plataforma B2B real no expone pedidos ni catálogos sin control de
acceso. Tampoco se pueden dejar secretos en el repositorio.

## Alternativas
JWT firmados por cada servicio (duplica la gestión de llaves), mTLS entre servicios (no cubre usuarios),
API keys estáticas (secretos compartidos, sin roles), **OAuth2/OIDC con Keycloak**.

## Decisión
Keycloak con el realm `mariposa`, importado al arrancar con placeholders que se resuelven desde variables de
entorno. `order-processor` obtiene tokens con **client credentials** para llamar a las APIs (roles
`products-reader` y `clients-reader`). La PWA usa **Authorization Code + PKCE**. Roles de usuario:
`orders-reader` y `orders-admin`. Todos los servicios validan firma (JWKS), emisor y expiración.
Los healthchecks y las métricas quedan públicos dentro de la red interna.

## Consecuencias
Un contenedor más y ~30 s extra de arranque. En AWS se reemplaza por Cognito o por Keycloak gestionado
sin tocar el código (sólo configuración del emisor).

## Cuándo revisarla
Si la organización ya tiene un IdP corporativo o si se necesita autorización fina por distribuidor (ABAC).
