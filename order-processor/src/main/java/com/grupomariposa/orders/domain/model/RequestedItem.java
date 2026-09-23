package com.grupomariposa.orders.domain.model;

import java.math.BigDecimal;
import java.util.Objects;

public record RequestedItem(String productId, int quantity, BigDecimal unitPrice) {

    private static final String INVALID_QUANTITY = "Quantity must be positive";
    private static final String NEGATIVE_PRICE = "Unit price cannot be negative";

    public RequestedItem {
        Objects.requireNonNull(productId, "productId");
        Objects.requireNonNull(unitPrice, "unitPrice");
        if (quantity <= 0) {
            throw new IllegalArgumentException(INVALID_QUANTITY);
        }
        if (unitPrice.signum() < 0) {
            throw new IllegalArgumentException(NEGATIVE_PRICE);
        }
    }
}
