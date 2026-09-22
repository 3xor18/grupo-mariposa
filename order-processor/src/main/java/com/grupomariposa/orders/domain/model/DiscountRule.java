package com.grupomariposa.orders.domain.model;

import java.util.Objects;

public record DiscountRule(Rate rate, int minimumQuantity) {

    private static final String NOT_A_FRACTION = "Discount rate must be between 0 and 1";
    private static final String INVALID_THRESHOLD = "Minimum quantity must be at least one";

    public DiscountRule {
        Objects.requireNonNull(rate, "rate");
        if (!rate.isFraction()) {
            throw new IllegalArgumentException(NOT_A_FRACTION);
        }
        if (minimumQuantity < 1) {
            throw new IllegalArgumentException(INVALID_THRESHOLD);
        }
    }

    public boolean appliesTo(final int quantity) {
        return quantity >= minimumQuantity;
    }
}
