package com.grupomariposa.orders.infrastructure.web.dto;

import java.math.BigDecimal;

public record OrderLineResponse(
        String productId,
        String name,
        String sku,
        String taxCategory,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal grossSubtotal,
        BigDecimal discountRate,
        BigDecimal discount,
        BigDecimal netSubtotal,
        BigDecimal taxRate,
        BigDecimal taxAmount,
        BigDecimal lineTotal) {
}
