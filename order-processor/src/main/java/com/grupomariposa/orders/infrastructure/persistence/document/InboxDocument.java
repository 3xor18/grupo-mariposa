package com.grupomariposa.orders.infrastructure.persistence.document;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = InboxDocument.COLLECTION)
public record InboxDocument(
        @Id String id,
        String orderId,
        int eventVersion,
        String outcome,
        Instant receivedAt) {

    public static final String COLLECTION = "inbox";
}
