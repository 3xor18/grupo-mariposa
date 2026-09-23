# syntax=docker/dockerfile:1.7
ARG BUILD_IMAGE=maven:3.9.16-eclipse-temurin-21
ARG BUILD_DIGEST=sha256:c2a2c58516d160f43b50f12baa427ca86989e0bc942609e04aff61da5d9a7d74
ARG RUNTIME_IMAGE=eclipse-temurin:21.0.12_8-jre-alpine
ARG RUNTIME_DIGEST=sha256:1a29e1fe337eb28b5bec30f0ee8ed29f0ff80ab6f75dcf9313efe82911065a52
FROM ${BUILD_IMAGE}@${BUILD_DIGEST} AS build
WORKDIR /workspace
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline
COPY src src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q package -DskipTests -Djacoco.skip=true
RUN cp target/config-server-*.jar target/application.jar \
    && java -Djarmode=tools -jar target/application.jar extract --layers --launcher \
       --destination extracted

FROM ${RUNTIME_IMAGE}@${RUNTIME_DIGEST}
ARG APP_UID=10001
RUN addgroup -S -g "${APP_UID}" app && adduser -S -u "${APP_UID}" -G app -H app
WORKDIR /app
COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/application/ ./
USER ${APP_UID}:${APP_UID}
EXPOSE 8888
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=65 -XX:+ExitOnOutOfMemoryError"
HEALTHCHECK --interval=10s --timeout=3s --retries=12 \
    CMD wget -qO- http://127.0.0.1:8888/actuator/health/liveness || exit 1
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
