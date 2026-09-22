package com.grupomariposa.orders.infrastructure.persistence.document;

import org.bson.types.Decimal128;

public record LineDocument(
        String productId,
        String name,
        String sku,
        String taxCategory,
        int quantity,
        Decimal128 unitPrice,
        Decimal128 grossSubtotal,
        Decimal128 discountRate,
        Decimal128 discount,
        Decimal128 netSubtotal,
        Decimal128 taxRate,
        Decimal128 taxAmount,
        Decimal128 lineTotal) {
}
