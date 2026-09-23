package com.grupomariposa.orders.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.grupomariposa.orders.infrastructure.persistence.document.InboxDocument;
import com.grupomariposa.orders.infrastructure.persistence.document.OrderDocument;
import com.grupomariposa.orders.infrastructure.persistence.document.OutboxDocument;
import com.grupomariposa.orders.infrastructure.persistence.document.TaxRateDocument;
import java.time.Duration;
import java.util.List;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.data.mongodb.core.index.IndexOperations;

class IndexInitializerTest {

    private static final PersistenceProperties PROPERTIES = new PersistenceProperties(5,
            Duration.ofMillis(20), Duration.ofDays(30), Duration.ofDays(7));

    private final MongoTemplate mongo = mock(MongoTemplate.class);
    private final IndexOperations orders = mock(IndexOperations.class);
    private final IndexOperations inbox = mock(IndexOperations.class);
    private final IndexOperations outbox = mock(IndexOperations.class);
    private final IndexOperations taxRates = mock(IndexOperations.class);

    @BeforeEach
    void setUp() {
        when(mongo.indexOps(OrderDocument.class)).thenReturn(orders);
        when(mongo.indexOps(OutboxDocument.class)).thenReturn(outbox);
        when(mongo.indexOps(InboxDocument.COLLECTION)).thenReturn(inbox);
        when(mongo.indexOps(OutboxDocument.COLLECTION)).thenReturn(outbox);
        when(mongo.indexOps(TaxRateDocument.class)).thenReturn(taxRates);
    }

    @Test
    void should_create_missing_ttl_indexes() {
        when(inbox.getIndexInfo()).thenReturn(List.of());
        when(outbox.getIndexInfo()).thenReturn(List.of());

        new IndexInitializer(mongo, PROPERTIES).afterSingletonsInstantiated();

        verify(inbox).createIndex(any());
        verify(taxRates, times(2)).createIndex(any());
        verify(mongo, never()).executeCommand(any(Document.class));
    }

    @Test
    void should_modify_ttl_in_place_when_retention_changes() {
        when(inbox.getIndexInfo()).thenReturn(List.of(ttlIndex("inbox_received_ttl", 60)));
        when(outbox.getIndexInfo()).thenReturn(List.of(ttlIndex("outbox_published_ttl",
                Duration.ofDays(7).toSeconds())));

        new IndexInitializer(mongo, PROPERTIES).afterSingletonsInstantiated();

        final ArgumentCaptor<Document> command = ArgumentCaptor.forClass(Document.class);
        verify(mongo).executeCommand(command.capture());
        assertThat(command.getValue().getString("collMod")).isEqualTo(InboxDocument.COLLECTION);
        assertThat(command.getValue().get("index", Document.class).get("expireAfterSeconds"))
                .isEqualTo(Duration.ofDays(30).toSeconds());
        verify(inbox, never()).createIndex(any());
        verify(mongo, never()).executeCommand(anyString());
    }

    private static IndexInfo ttlIndex(final String name, final long seconds) {
        return IndexInfo.indexInfoOf(new Document("name", name)
                .append("key", new Document("field", 1))
                .append("expireAfterSeconds", seconds));
    }
}
