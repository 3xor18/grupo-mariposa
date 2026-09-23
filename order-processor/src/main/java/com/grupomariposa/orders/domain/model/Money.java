package com.grupomariposa.orders.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public record Money(BigDecimal amount) {

    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    public Money {
        Objects.requireNonNull(amount, "amount");
    }

    public static Money of(final String amount) {
        return new Money(new BigDecimal(amount));
    }

    public static Money rounded(final BigDecimal amount, final int fractionDigits) {
        return new Money(amount.setScale(fractionDigits, ROUNDING));
    }

    public static Money zero(final int fractionDigits) {
        return rounded(BigDecimal.ZERO, fractionDigits);
    }

    public static Money ofUnits(final BigDecimal unitPrice, final int quantity,
                                final int fractionDigits) {
        return rounded(unitPrice.multiply(BigDecimal.valueOf(quantity)), fractionDigits);
    }

    public Money plus(final Money other) {
        return new Money(amount.add(other.amount));
    }

    public Money minus(final Money other) {
        return new Money(amount.subtract(other.amount));
    }

    public Money times(final Rate rate) {
        return rounded(amount.multiply(rate.value()), amount.scale());
    }
}
