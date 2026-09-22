package com.grupomariposa.orders.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.grupomariposa.orders.application.port.out.EventPublisher;
import com.grupomariposa.orders.application.port.out.PendingEvent;
import com.grupomariposa.orders.application.port.out.ProcessingObserver;
import com.grupomariposa.orders.application.service.PublishPendingEventsService;
import com.grupomariposa.orders.application.service.RelaySettings;
import com.grupomariposa.orders.infrastructure.persistence.MongoOutboxStore;
import com.grupomariposa.orders.infrastructure.persistence.document.Fields;
import com.grupomariposa.orders.infrastructure.persistence.document.OutboxDocument;
import com.grupomariposa.orders.infrastructure.persistence.document.OutboxStatus;
import com.grupomariposa.orders.support.IntegrationTest;
import com.grupomariposa.orders.support.TopicProbe;
import com.mongodb.client.MongoClient;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

class OutboxLeaseIT extends IntegrationTest {

    private static final Instant LATER = Instant.now().plus(Duration.ofDays(1));
    private static final int EVENTS = 20;

    @Autowired
    private MongoClient mongoClient;

    @Autowired
    private EventPublisher publisher;

    @Autowired
    private ProcessingObserver observer;

    private MongoTemplate isolated;
    private MongoOutboxStore store;

    @BeforeEach
    void isolatedDatabase() {
        isolated = new MongoTemplate(mongoClient, "relay-" + UUID.randomUUID());
        store = new MongoOutboxStore(isolated);
    }

    @AfterEach
    void dropDatabase() {
        isolated.getDb().drop();
    }

    @Test
    void should_fence_publication_and_release_to_the_current_lease_owner() {
        final OutboxDocument entry = pending("ORD-FENCE-" + UUID.randomUUID(), 1);
        isolated.insert(entry);

        assertThat(store.claim(10, LATER, LATER.plusSeconds(1), "relay-a"))
                .extracting(PendingEvent::eventId).containsExactly(entry.id());
        assertThat(store.claim(10, LATER.plusSeconds(2), LATER.plusSeconds(32), "relay-b"))
                .extracting(PendingEvent::eventId).containsExactly(entry.id());

        assertThat(store.markPublished(entry.id(), "relay-a", LATER)).isFalse();
        assertThat(store.markPublished(entry.id(), "relay-b", LATER)).isTrue();
        assertThat(store.release(entry.id(), "relay-a", 1, LATER)).isFalse();
        assertThat(status(entry.id())).isEqualTo(OutboxStatus.PUBLISHED);
    }

    @Test
    void should_publish_versions_of_the_same_order_in_order() {
        final String orderId = "ORD-ORDERED-" + UUID.randomUUID();
        final OutboxDocument second = pending(orderId, 2);
        final OutboxDocument first = pending(orderId, 1);
        isolated.insert(second);
        isolated.insert(first);

        assertThat(store.claim(10, LATER, LATER.plusSeconds(30), "relay-a"))
                .extracting(PendingEvent::eventId).containsExactly(first.id());
        assertThat(store.claim(10, LATER, LATER.plusSeconds(30), "relay-b")).isEmpty();
        assertThat(store.markPublished(first.id(), "relay-a", LATER)).isTrue();
        assertThat(store.claim(10, LATER, LATER.plusSeconds(30), "relay-b"))
                .extracting(PendingEvent::eventId).containsExactly(second.id());
    }

    @Test
    void should_publish_each_event_once_with_two_competing_relays() {
        final String prefix = "ORD-RACE-RELAY-" + UUID.randomUUID() + "-";
        IntStream.range(0, EVENTS).forEach(index -> isolated.insert(present(prefix + index)));
        final PublishPendingEventsService relayA = relay("relay-a");
        final PublishPendingEventsService relayB = relay("relay-b");

        await().atMost(Duration.ofSeconds(30)).until(() -> {
            CompletableFuture.allOf(CompletableFuture.runAsync(relayA::publishPending),
                    CompletableFuture.runAsync(relayB::publishPending)).join();
            return isolated.count(Query.query(Criteria.where(Fields.STATUS)
                    .is(OutboxStatus.PUBLISHED.name())), OutboxDocument.class) == EVENTS;
        });

        try (TopicProbe processed = new TopicProbe(KAFKA.getBootstrapServers(),
                ORDERS_PROCESSED)) {
            final List<ConsumerRecord<String, byte[]>> records = processed.await(
                    record -> record.key() != null && record.key().startsWith(prefix), EVENTS,
                    Duration.ofSeconds(20), Duration.ofSeconds(2));
            assertThat(records).hasSize(EVENTS);
            assertThat(records).extracting(ConsumerRecord::key).doesNotHaveDuplicates();
        }
    }

    private PublishPendingEventsService relay(final String owner) {
        return new PublishPendingEventsService(store, publisher, Instant::now, observer,
                new RelaySettings(5, Duration.ofSeconds(30), Duration.ofSeconds(10),
                        Duration.ofMillis(100), Duration.ofSeconds(1), owner));
    }

    private OutboxStatus status(final String id) {
        return isolated.findById(id, OutboxDocument.class).status();
    }

    private static OutboxDocument pending(final String orderId, final int version) {
        return entry(orderId, version, LATER.minusSeconds(10));
    }

    private static OutboxDocument present(final String orderId) {
        return entry(orderId, 1, Instant.now().minusSeconds(1));
    }

    private static OutboxDocument entry(final String orderId, final int version,
                                        final Instant availableAt) {
        return new OutboxDocument(UUID.randomUUID().toString(), orderId, version,
                ORDERS_PROCESSED, orderId, "{\"orderId\":\"" + orderId + "\"}",
                OutboxStatus.PENDING, 0, null, null, availableAt, availableAt, null);
    }
}
