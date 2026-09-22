package com.grupomariposa.orders.support;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.security.SecureRandom;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@AutoConfigureObservability
@ActiveProfiles("test")
public abstract class IntegrationTest {

    protected static final String ORDERS_CREATED = "orders.created.v1";
    protected static final String ORDERS_PROCESSED = "orders.processed.v1";
    protected static final String DLT = "orders.processing.dlt";
    private static final int REDIS_PORT = 6379;
    private static final int KEY_BYTES = 32;

    protected static final MongoDBContainer MONGO =
            new MongoDBContainer(DockerImageName.parse("mongo:7.0"));
    protected static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("apache/kafka:3.9.1"));
    protected static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(REDIS_PORT);
    protected static final WireMockServer WIREMOCK =
            new WireMockServer(wireMockConfig().dynamicPort());
    protected static final String PII_KEY = randomKey();

    static {
        Startables.deepStart(MONGO, KAFKA, REDIS).join();
        WIREMOCK.start();
    }

    @Autowired
    private CircuitBreakerRegistry circuitBreakers;

    protected final DependencyStubs stubs = new DependencyStubs(WIREMOCK);

    @DynamicPropertySource
    static void infrastructure(final DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", () -> MONGO.getReplicaSetUrl("orders"));
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(REDIS_PORT));
        registry.add("app.http.clients.base-url", WIREMOCK::baseUrl);
        registry.add("app.http.products.base-url", WIREMOCK::baseUrl);
        registry.add("app.http.oauth.token-uri",
                () -> WIREMOCK.baseUrl() + DependencyStubs.TOKEN_PATH);
        registry.add("OAUTH_CLIENT_SECRET", () -> "integration-client-secret");
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> WIREMOCK.baseUrl() + "/jwks");
        registry.add("PII_ENCRYPTION_KEY", () -> PII_KEY);
    }

    @BeforeEach
    void resetDependencies() {
        WIREMOCK.resetAll();
        stubs.token();
        circuitBreakers.getAllCircuitBreakers().forEach(CircuitBreaker::reset);
    }

    protected KafkaTestClient kafka() {
        return new KafkaTestClient(KAFKA.getBootstrapServers());
    }

    private static String randomKey() {
        final byte[] key = new byte[KEY_BYTES];
        new SecureRandom().nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }
}
