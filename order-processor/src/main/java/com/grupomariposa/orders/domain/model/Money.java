package com.grupomariposa.orders.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public record Money(BigDecimal amount) {

    public static final int SCALE = 2;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
    public static final Money ZERO = new Money(BigDecimal.ZERO);

    public Money {
        Objects.requireNonNull(amount, "amount");
        amount = amount.setScale(SCALE, ROUNDING);
    }

    public static Money of(final BigDecimal amount) {
        return new Money(amount);
    }

    public static Money of(final String amount) {
        return new Money(new BigDecimal(amount));
    }

    public static Money ofUnits(final BigDecimal unitPrice, final int quantity) {
        return new Money(unitPrice.multiply(BigDecimal.valueOf(quantity)));
    }

    public Money plus(final Money other) {
        return new Money(amount.add(other.amount));
    }

    public Money minus(final Money other) {
        return new Money(amount.subtract(other.amount));
    }

    public Money times(final Rate rate) {
        return new Money(amount.multiply(rate.value()));
    }
}
