package com.grupomariposa.orders.infrastructure.persistence.document;

import java.time.Instant;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = OrderDocument.COLLECTION)
public record OrderDocument(
        @Id String id,
        String sourceEventId,
        int eventVersion,
        String status,
        String market,
        String currency,
        String channel,
        ClientDocument client,
        List<LineDocument> lines,
        TotalsDocument totals,
        String reason,
        List<ViolationDocument> violations,
        FailureDocument failure,
        Instant occurredAt,
        Instant receivedAt,
        Instant processedAt,
        String traceId) {

    public static final String COLLECTION = "orders";
}
