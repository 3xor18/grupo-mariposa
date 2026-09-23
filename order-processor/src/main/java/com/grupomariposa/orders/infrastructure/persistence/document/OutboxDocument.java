package com.grupomariposa.orders.infrastructure.persistence.document;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = OutboxDocument.COLLECTION)
public record OutboxDocument(
        @Id String id,
        String orderId,
        int eventVersion,
        String topic,
        String key,
        String payload,
        OutboxStatus status,
        int attempts,
        Instant leaseUntil,
        String leaseOwner,
        Instant availableAt,
        Instant createdAt,
        Instant publishedAt) {

    public static final String COLLECTION = "outbox";
}
