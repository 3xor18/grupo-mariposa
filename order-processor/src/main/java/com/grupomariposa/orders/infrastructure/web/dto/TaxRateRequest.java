package com.grupomariposa.orders.infrastructure.web.dto;

import java.math.BigDecimal;

public record TaxRateRequest(
        String market,
        String category,
        BigDecimal rate,
        String validFrom,
        String validTo,
        String changeReason) {
}
