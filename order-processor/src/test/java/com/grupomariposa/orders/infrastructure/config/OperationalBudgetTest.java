package com.grupomariposa.orders.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import com.grupomariposa.orders.infrastructure.http.HttpDependenciesProperties;
import com.grupomariposa.orders.infrastructure.kafka.MessagingProperties;
import com.grupomariposa.orders.infrastructure.kafka.OutboxRelayProperties;
import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;

class OperationalBudgetTest {

    private static final MessagingProperties.RecordRetry RECORD_RETRY =
            new MessagingProperties.RecordRetry(Duration.ofSeconds(1), 2.0, 4);
    private static final OutboxRelayProperties RELAY = relay(Duration.ofSeconds(15),
            Duration.ofSeconds(30));

    @Test
    void should_accept_default_budget() {
        final OperationalBudget budget = new OperationalBudget(RECORD_RETRY, http(32),
                32, 10, Duration.ofMinutes(5), Duration.ofSeconds(10), RELAY);

        assertThat(budget.attemptBudget()).isEqualTo(Duration.ofMillis(12_000));
        assertThat(budget.pollCycleBudget()).isEqualTo(Duration.ofSeconds(124));
        assertThat(budget.verify()).isSameAs(budget);
    }

    @Test
    void should_report_every_unsafe_combination() {
        final OperationalBudget budget = new OperationalBudget(RECORD_RETRY, http(8), 64, 50,
                Duration.ofMinutes(1), Duration.ofSeconds(120),
                relay(Duration.ofSeconds(30), Duration.ofSeconds(30)));

        assertThat(budget.violations()).hasSize(4);
        assertThatIllegalStateException().isThrownBy(budget::verify)
                .withMessageContaining("bulkhead")
                .withMessageContaining("max.poll.interval.ms")
                .withMessageContaining("delivery.timeout.ms")
                .withMessageContaining("lease");
    }

    @Test
    void should_read_kafka_client_settings_with_fallbacks() {
        final KafkaProperties kafka = new KafkaProperties();
        final MessagingProperties messaging = new MessagingProperties(null, "group", 1, false,
                (short) 1, RECORD_RETRY, Duration.ofSeconds(2));

        final OperationalBudget defaults = OperationalBudget.of(messaging, http(32),
                new ProcessingProperties(32, "node"), RELAY, kafka);
        kafka.getConsumer().setMaxPollRecords(10);
        kafka.getConsumer().getProperties().put(OperationalBudget.MAX_POLL_INTERVAL, "60000");
        kafka.getProducer().getProperties().put(OperationalBudget.DELIVERY_TIMEOUT, " 9000 ");
        final OperationalBudget configured = OperationalBudget.of(messaging, http(32),
                new ProcessingProperties(32, "node"), RELAY, kafka);

        assertThat(defaults.maxPollRecords()).isEqualTo(OperationalBudget.DEFAULT_MAX_POLL_RECORDS);
        assertThat(defaults.producerDeliveryTimeout()).isEqualTo(Duration.ofMinutes(2));
        assertThat(configured.maxPollRecords()).isEqualTo(10);
        assertThat(configured.maxPollInterval()).isEqualTo(Duration.ofMinutes(1));
        assertThat(configured.producerDeliveryTimeout()).isEqualTo(Duration.ofSeconds(9));
    }

    private static OutboxRelayProperties relay(final Duration sendTimeout, final Duration lease) {
        return new OutboxRelayProperties(true, Duration.ofMillis(250), 100, lease, sendTimeout,
                Duration.ofSeconds(1), Duration.ofSeconds(60));
    }

    private static HttpDependenciesProperties http(final int bulkhead) {
        final HttpDependenciesProperties.Endpoint endpoint =
                new HttpDependenciesProperties.Endpoint(URI.create("http://localhost"));
        return new HttpDependenciesProperties(endpoint, endpoint, Duration.ofMillis(500),
                Duration.ofSeconds(2), new HttpDependenciesProperties.OAuth(false, "id",
                URI.create("http://localhost/token"), "client"),
                new HttpDependenciesProperties.Resilience(3, Duration.ofMillis(200), 2.0, 0.5,
                        Duration.ofSeconds(2), 20, 10, 50f, Duration.ofSeconds(10), 3, bulkhead,
                        Duration.ofMillis(500)));
    }
}
