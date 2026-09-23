package com.grupomariposa.orders.infrastructure.web.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record TaxRateResponse(
        String id,
        String market,
        String category,
        BigDecimal rate,
        Instant validFrom,
        Instant validTo,
        String status,
        String proposedBy,
        Instant proposedAt,
        String reviewedBy,
        Instant reviewedAt,
        String changeReason,
        long version) {
}
