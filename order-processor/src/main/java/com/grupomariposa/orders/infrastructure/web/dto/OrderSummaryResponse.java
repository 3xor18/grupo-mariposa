package com.grupomariposa.orders.infrastructure.web.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderSummaryResponse(
        String orderId,
        String status,
        String market,
        String currency,
        String clientId,
        int eventVersion,
        BigDecimal grandTotal,
        String reason,
        Instant processedAt) {
}
