# syntax=docker/dockerfile:1.7
FROM maven:3.9.12-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline
COPY src src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q package -DskipTests -Djacoco.skip=true
RUN cp target/config-server-*.jar target/application.jar \
    && java -Djarmode=tools -jar target/application.jar extract --layers --launcher \
       --destination extracted

FROM eclipse-temurin:21.0.9_10-jre-alpine
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
