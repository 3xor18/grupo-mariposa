package com.grupomariposa.orders.infrastructure.messaging;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record OrderProcessedPayload(
        String eventId,
        int eventVersion,
        Instant occurredAt,
        String sourceEventId,
        String orderId,
        String clientId,
        String status,
        String market,
        String currency,
        TotalsPayload totals,
        String reason,
        List<ViolationPayload> violations) {

    public record TotalsPayload(
            BigDecimal grossSubtotal,
            BigDecimal discount,
            BigDecimal netSubtotal,
            BigDecimal tax,
            BigDecimal grandTotal) {
    }

    public record ViolationPayload(String code, String message, String productId) {
    }
}
