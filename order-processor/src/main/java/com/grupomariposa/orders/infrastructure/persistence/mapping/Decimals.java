package com.grupomariposa.orders.infrastructure.persistence.mapping;

import com.grupomariposa.orders.domain.model.Money;
import com.grupomariposa.orders.domain.model.Rate;
import java.math.BigDecimal;
import org.bson.types.Decimal128;

final class Decimals {

    private Decimals() {
    }

    static Decimal128 of(final BigDecimal value) {
        return new Decimal128(value);
    }

    static Decimal128 of(final Money money) {
        return new Decimal128(money.amount());
    }

    static Decimal128 of(final Rate rate) {
        return new Decimal128(rate.value());
    }

    static BigDecimal toBigDecimal(final Decimal128 value) {
        return value.bigDecimalValue();
    }

    static Money toMoney(final Decimal128 value) {
        return new Money(value.bigDecimalValue());
    }

    static Rate toRate(final Decimal128 value) {
        return new Rate(value.bigDecimalValue());
    }
}
