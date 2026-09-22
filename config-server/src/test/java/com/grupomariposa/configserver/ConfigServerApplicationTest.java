package com.grupomariposa.configserver;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringApplicationHook;
import org.springframework.boot.SpringApplicationRunListener;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.HttpStatus;

@SpringBootTest(
        webEnvironment = WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.security.user.name=" + ConfigServerApplicationTest.USERNAME,
            "spring.security.user.password=" + ConfigServerApplicationTest.PASSWORD,
            "spring.cloud.config.server.native.search-locations=classpath:/test-config-repo"
        })
class ConfigServerApplicationTest {

    static final String USERNAME = "reader";
    static final String PASSWORD = "test-only-password";
    private static final String SAMPLE_APP_PATH = "/sample-service/docker";
    private static final String SAMPLE_PROPERTY = "sample.timeout-ms";
    private static final String PROMETHEUS_PATH = "/actuator/prometheus";
    private static final String LIVENESS_PATH = "/actuator/health/liveness";

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void should_serve_configuration_when_credentials_are_valid() {
        var response = restTemplate.withBasicAuth(USERNAME, PASSWORD)
                .getForEntity(SAMPLE_APP_PATH, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains(SAMPLE_PROPERTY);
    }

    @Test
    void should_reject_request_when_credentials_are_missing() {
        var response = restTemplate.getForEntity(SAMPLE_APP_PATH, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void should_expose_health_without_credentials() {
        var response = restTemplate.getForEntity(LIVENESS_PATH, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void should_require_credentials_for_prometheus_metrics() {
        var anonymous = restTemplate.getForEntity(PROMETHEUS_PATH, String.class);
        var authenticated = restTemplate.withBasicAuth(USERNAME, PASSWORD)
                .getForEntity(PROMETHEUS_PATH, String.class);

        assertThat(anonymous.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(authenticated.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void should_start_application_from_main() {
        List<ConfigurableApplicationContext> contexts = new ArrayList<>();
        SpringApplicationHook hook = application -> new SpringApplicationRunListener() {
            @Override
            public void started(ConfigurableApplicationContext context, Duration timeTaken) {
                contexts.add(context);
            }
        };

        SpringApplication.withHook(hook, () -> ConfigServerApplication.main(new String[] {
            "--server.port=0",
            "--spring.security.user.name=" + USERNAME,
            "--spring.security.user.password=" + PASSWORD,
            "--spring.cloud.config.server.native.search-locations=classpath:/test-config-repo",
            "--management.server.port=0"
        }));

        try (var context = contexts.getFirst()) {
            assertThat(context.isRunning()).isTrue();
        }
    }
}
