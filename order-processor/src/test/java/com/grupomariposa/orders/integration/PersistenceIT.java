package com.grupomariposa.orders.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.grupomariposa.orders.application.ApplicationFixtures;
import com.grupomariposa.orders.application.port.out.OrderStore;
import com.grupomariposa.orders.application.service.OrderAssembler;
import com.grupomariposa.orders.domain.model.FailureDetails;
import com.grupomariposa.orders.domain.model.Order;
import com.grupomariposa.orders.infrastructure.persistence.IndexInitializer;
import com.grupomariposa.orders.infrastructure.persistence.PersistenceProperties;
import com.grupomariposa.orders.infrastructure.persistence.document.InboxDocument;
import com.grupomariposa.orders.support.IntegrationTest;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexInfo;

class PersistenceIT extends IntegrationTest {

    private static final FailureDetails FAILURE =
            new FailureDetails("EXTERNAL_TRANSIENT", "products-api responded 503", 4);

    @Autowired
    private OrderStore store;

    @Autowired
    private OrderAssembler assembler;

    @Autowired
    private MongoTemplate mongo;

    @Autowired
    private PersistenceProperties properties;

    @Test
    void should_only_replace_technical_failure_of_the_same_event_at_equal_version() {
        final String orderId = "ORD-TF-" + UUID.randomUUID();

        assertThat(store.saveTechnicalFailure(failure(orderId, "EVT-A", 1))).isTrue();
        assertThat(store.saveTechnicalFailure(failure(orderId, "EVT-A", 1))).isTrue();
        assertThat(store.saveTechnicalFailure(failure(orderId, "EVT-B", 1))).isFalse();
        assertThat(store.findState(orderId).orElseThrow().sourceEventId()).isEqualTo("EVT-A");
        assertThat(store.saveTechnicalFailure(failure(orderId, "EVT-B", 2))).isTrue();
    }

    @Test
    void should_change_ttl_of_existing_index_in_place() {
        final PersistenceProperties shorter = new PersistenceProperties(
                properties.transactionAttempts(), properties.transactionRetryBackoff(),
                Duration.ofDays(1), properties.outboxRetention());

        new IndexInitializer(mongo, shorter).afterSingletonsInstantiated();
        assertThat(inboxTtl()).contains(Duration.ofDays(1));

        new IndexInitializer(mongo, properties).afterSingletonsInstantiated();
        assertThat(inboxTtl()).contains(properties.inboxRetention());
    }

    private Optional<Duration> inboxTtl() {
        return mongo.indexOps(InboxDocument.COLLECTION).getIndexInfo().stream()
                .filter(index -> "inbox_received_ttl".equals(index.getName()))
                .findFirst()
                .flatMap(IndexInfo::getExpireAfter);
    }

    private Order failure(final String orderId, final String eventId, final int version) {
        return assembler.technicalFailure(
                ApplicationFixtures.command(orderId, eventId, version), FAILURE);
    }
}
