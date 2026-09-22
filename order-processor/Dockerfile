ARG BUILD_IMAGE=maven:3.9.16-eclipse-temurin-21
ARG BUILD_DIGEST=sha256:c2a2c58516d160f43b50f12baa427ca86989e0bc942609e04aff61da5d9a7d74
ARG RUNTIME_IMAGE=eclipse-temurin:21.0.12_8-jre-alpine
ARG RUNTIME_DIGEST=sha256:1a29e1fe337eb28b5bec30f0ee8ed29f0ff80ab6f75dcf9313efe82911065a52

FROM ${BUILD_IMAGE}@${BUILD_DIGEST} AS build
WORKDIR /workspace
COPY pom.xml ./
COPY config config
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline -DskipTests
COPY src src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q package -DskipTests \
    -Dcheckstyle.skip=true -Dspotbugs.skip=true -Djacoco.skip=true
RUN cp target/order-processor-*.jar target/application.jar \
    && java -Djarmode=tools -jar target/application.jar extract --layers --launcher \
       --destination target/extracted

FROM ${RUNTIME_IMAGE}@${RUNTIME_DIGEST}
RUN addgroup -S -g 10001 orders && adduser -S -u 10001 -G orders -H -s /sbin/nologin orders
WORKDIR /app
COPY --from=build --chown=10001:10001 /workspace/target/extracted/dependencies/ ./
COPY --from=build --chown=10001:10001 /workspace/target/extracted/spring-boot-loader/ ./
COPY --from=build --chown=10001:10001 /workspace/target/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=10001:10001 /workspace/target/extracted/application/ ./
USER 10001:10001
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:InitialRAMPercentage=50 -XX:+ExitOnOutOfMemoryError -XX:+UseZGC -Djava.security.egd=file:/dev/./urandom"
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=3s --start-period=60s --retries=3 \
    CMD wget -q -O /dev/null http://127.0.0.1:8080/health/live || exit 1
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
