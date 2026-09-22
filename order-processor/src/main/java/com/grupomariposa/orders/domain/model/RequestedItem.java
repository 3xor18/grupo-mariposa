package com.grupomariposa.orders.domain.model;

import java.math.BigDecimal;
import java.util.Objects;

public record RequestedItem(String productId, int quantity, BigDecimal unitPrice) {

    public RequestedItem {
        Objects.requireNonNull(productId, "productId");
        Objects.requireNonNull(unitPrice, "unitPrice");
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        if (unitPrice.signum() < 0) {
            throw new IllegalArgumentException("Unit price cannot be negative");
        }
    }
}
