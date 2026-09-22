package com.grupomariposa.orders.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.grupomariposa.orders.infrastructure.persistence.document.OutboxDocument;
import com.grupomariposa.orders.infrastructure.persistence.document.OutboxStatus;
import com.grupomariposa.orders.support.IntegrationTest;
import com.grupomariposa.orders.support.TopicProbe;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;

class OutboxRelayIT extends IntegrationTest {

    @Autowired
    private MongoTemplate mongo;

    @Test
    void should_publish_entries_left_behind_by_a_crashed_instance() {
        final Instant now = Instant.now();
        final OutboxDocument pending = entry("PENDING", OutboxStatus.PENDING, null, now);
        final OutboxDocument abandoned = entry("ABANDONED", OutboxStatus.IN_FLIGHT,
                now.minusSeconds(60), now);
        final OutboxDocument leased = entry("LEASED", OutboxStatus.IN_FLIGHT,
                now.plus(Duration.ofMinutes(10)), now);
        mongo.insert(pending);
        mongo.insert(abandoned);
        mongo.insert(leased);

        try (TopicProbe processed = new TopicProbe(KAFKA.getBootstrapServers(),
                ORDERS_PROCESSED)) {
            assertThat(processed.awaitKey(pending.key(), 1)).singleElement().satisfies(received ->
                    assertThat(new String(received.value(), StandardCharsets.UTF_8))
                            .isEqualTo(pending.payload()));
            assertThat(processed.awaitKey(abandoned.key(), 1)).hasSize(1);
            assertThat(processed.await(received -> leased.key().equals(received.key()), 1,
                    Duration.ofSeconds(2), Duration.ZERO)).isEmpty();
        }
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(status(pending.id())).isEqualTo(OutboxStatus.PUBLISHED);
            assertThat(status(abandoned.id())).isEqualTo(OutboxStatus.PUBLISHED);
        });
        assertThat(status(leased.id())).isEqualTo(OutboxStatus.IN_FLIGHT);
    }

    private OutboxStatus status(final String id) {
        return mongo.findById(id, OutboxDocument.class).status();
    }

    private static OutboxDocument entry(final String label, final OutboxStatus status,
                                        final Instant leaseUntil, final Instant now) {
        final String orderId = "ORD-RELAY-" + label + "-" + UUID.randomUUID();
        return new OutboxDocument(UUID.randomUUID().toString(), orderId, 1,
                ORDERS_PROCESSED, orderId, "{\"orderId\":\"" + orderId + "\"}", status, 0,
                leaseUntil, leaseUntil == null ? null : "crashed-node", now.minusSeconds(5),
                now.minusSeconds(5), null);
    }
}
