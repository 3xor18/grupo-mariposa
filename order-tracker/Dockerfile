# syntax=docker/dockerfile:1.7

ARG FLUTTER_IMAGE=ghcr.io/cirruslabs/flutter:stable
ARG NGINX_IMAGE=nginxinc/nginx-unprivileged:alpine

FROM ${FLUTTER_IMAGE} AS dependencies
WORKDIR /app
COPY pubspec.yaml pubspec.lock analysis_options.yaml ./
RUN flutter pub get --enforce-lockfile

FROM dependencies AS test
COPY lib lib
COPY test test
COPY web web
RUN flutter analyze && flutter test --coverage

FROM dependencies AS build
COPY lib lib
COPY web web
RUN flutter build web --release --no-web-resources-cdn --no-wasm-dry-run --no-source-maps \
    && find build/web -name '*.symbols' -delete

FROM ${NGINX_IMAGE} AS runtime
ENV ORDERS_API_URL=http://order-processor:8080 \
    API_BASE_URL=/api \
    KEYCLOAK_URL=http://localhost:8180 \
    KEYCLOAK_REALM=mariposa \
    KEYCLOAK_CLIENT_ID=order-tracker \
    NGINX_RESOLVER=127.0.0.11 \
    NGINX_ENVSUBST_FILTER="^(ORDERS_API_URL|KEYCLOAK_URL|NGINX_RESOLVER)$"
COPY --from=build /app/build/web /usr/share/nginx/html
COPY nginx/default.conf.template /etc/nginx/templates/default.conf.template
COPY nginx/config.json.template /etc/order-tracker/config.json.template
COPY --chmod=0755 nginx/40-order-tracker-runtime-config.sh /docker-entrypoint.d/
USER 101
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=3s --start-period=5s --retries=3 \
    CMD wget -q -O /dev/null http://127.0.0.1:8080/healthz || exit 1
