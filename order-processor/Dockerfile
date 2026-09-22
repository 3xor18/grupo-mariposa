FROM maven:3.9-eclipse-temurin-21 AS build
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

FROM eclipse-temurin:21-jre-alpine
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
