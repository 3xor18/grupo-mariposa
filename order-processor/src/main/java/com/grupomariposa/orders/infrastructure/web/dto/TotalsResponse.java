package com.grupomariposa.orders.infrastructure.web.dto;

import java.math.BigDecimal;

public record TotalsResponse(
        BigDecimal grossSubtotal,
        BigDecimal discount,
        BigDecimal netSubtotal,
        BigDecimal tax,
        BigDecimal grandTotal) {
}
