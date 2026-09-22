package com.grupomariposa.orders.domain.model;

import java.math.BigDecimal;
import java.util.Objects;

public record Rate(BigDecimal value) {

    private static final int PERCENT_SCALE_SHIFT = 2;

    public static final Rate ZERO = ofPercent(0);

    public Rate {
        Objects.requireNonNull(value, "value");
        if (value.signum() < 0) {
            throw new IllegalArgumentException("Rate cannot be negative");
        }
    }

    public static Rate ofPercent(final int percent) {
        return new Rate(BigDecimal.valueOf(percent).movePointLeft(PERCENT_SCALE_SHIFT));
    }

    public boolean isZero() {
        return value.signum() == 0;
    }
}
