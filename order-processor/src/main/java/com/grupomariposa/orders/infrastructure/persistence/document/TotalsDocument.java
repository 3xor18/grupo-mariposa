package com.grupomariposa.orders.infrastructure.persistence.document;

import org.bson.types.Decimal128;

public record TotalsDocument(
        Decimal128 grossSubtotal,
        Decimal128 discount,
        Decimal128 netSubtotal,
        Decimal128 tax,
        Decimal128 grandTotal) {
}
