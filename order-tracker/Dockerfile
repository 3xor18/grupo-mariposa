# syntax=docker/dockerfile:1.7

ARG FLUTTER_TAG=3.44.0
ARG FLUTTER_DIGEST=sha256:46691e311715845de03a3ba4753a475476936805b29431b1f00f1816981033f8
ARG NGINX_TAG=1.31.6-alpine3.24
ARG NGINX_DIGEST=sha256:e75f89810bf5bfbcf58a1cfb32a1a11de55b7623d732e67735d513b720d7436a

FROM ghcr.io/cirruslabs/flutter:${FLUTTER_TAG}@${FLUTTER_DIGEST} AS dependencies
WORKDIR /app
COPY pubspec.yaml pubspec.lock analysis_options.yaml ./
RUN flutter pub get --enforce-lockfile

FROM dependencies AS test
COPY lib lib
COPY test test
COPY web web
RUN flutter analyze && flutter test --coverage && touch /tmp/flutter-tests.passed

FROM dependencies AS build
COPY lib lib
COPY web web
RUN flutter build web --release --no-web-resources-cdn --no-wasm-dry-run --no-source-maps \
    && find build/web -name '*.symbols' -delete \
    && rm build/web/config.json

FROM nginxinc/nginx-unprivileged:${NGINX_TAG}@${NGINX_DIGEST} AS runtime-config-test
USER 0
RUN apk add --no-cache shellcheck
WORKDIR /nginx
COPY nginx/ ./
RUN shellcheck order-tracker-config.sh 10-order-tracker-config.envsh tests/*.sh \
    && sh tests/order-tracker-config.test.sh order-tracker-config.sh \
    && touch /tmp/runtime-config-tests.passed

FROM nginxinc/nginx-unprivileged:${NGINX_TAG}@${NGINX_DIGEST} AS runtime
ENV NGINX_ENVSUBST_FILTER="^(ORDERS_API_URL|NGINX_RESOLVER|CSP_CONNECT_SOURCES|HSTS_HEADER)$" \
    CONFIG_APP_NAME=order-tracker \
    CONFIG_PROFILE=default \
    CONFIG_SERVER_FAIL_FAST=true
COPY --from=test /tmp/flutter-tests.passed /etc/order-tracker/
COPY --from=runtime-config-test /tmp/runtime-config-tests.passed /etc/order-tracker/
COPY --from=build /app/build/web /usr/share/nginx/html
COPY nginx/default.conf.template /etc/nginx/templates/default.conf.template
COPY nginx/config.json.template /etc/order-tracker/config.json.template
COPY --chmod=0755 nginx/order-tracker-config.sh /usr/local/bin/order-tracker-config
COPY --chmod=0755 nginx/10-order-tracker-config.envsh /docker-entrypoint.d/
USER 101
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=3s --start-period=20s --retries=3 \
    CMD wget -q -O /dev/null http://127.0.0.1:8080/healthz || exit 1
