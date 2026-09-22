package com.grupomariposa.orders.infrastructure.web.dto;

import java.time.Instant;
import java.util.List;

public record OrderResponse(
        String orderId,
        String sourceEventId,
        int eventVersion,
        String status,
        String market,
        String currency,
        String channel,
        ClientSnapshotResponse client,
        List<OrderLineResponse> lines,
        TotalsResponse totals,
        String reason,
        List<ViolationResponse> violations,
        FailureResponse failure,
        Instant occurredAt,
        Instant receivedAt,
        Instant processedAt,
        String traceId) {
}
