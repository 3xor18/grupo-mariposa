package com.grupomariposa.orders.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MoneyTest {

    @ParameterizedTest(name = "{0} with {1} decimals rounds to {2}")
    @CsvSource({
        "1.005, 2, 1.01",
        "2.675, 2, 2.68",
        "0.125, 2, 0.13",
        "10.0049, 2, 10.00",
        "132.2304, 2, 132.23",
        "0.004, 2, 0.00",
        "-1.005, 2, -1.01",
        "7, 2, 7.00",
        "1432.8, 0, 1433",
        "1432.5, 0, 1433",
        "1432.49, 0, 1432",
        "8802.13, 0, 8802"
    })
    void should_round_half_up_to_currency_decimals(final String raw, final int digits,
                                                  final String expected) {
        assertThat(Money.rounded(new BigDecimal(raw), digits))
                .isEqualTo(Money.of(expected));
    }

    @Test
    void should_multiply_unit_price_by_quantity_and_round_once() {
        assertThat(Money.ofUnits(new BigDecimal("0.335"), 3, 2)).isEqualTo(Money.of("1.01"));
        assertThat(Money.ofUnits(new BigDecimal("1990"), 24, 0)).isEqualTo(Money.of("47760"));
    }

    @Test
    void should_add_and_subtract_amounts() {
        final Money base = Money.of("10.10");

        assertThat(base.plus(Money.of("0.05"))).isEqualTo(Money.of("10.15"));
        assertThat(base.minus(Money.of("0.15"))).isEqualTo(Money.of("9.95"));
    }

    @Test
    void should_keep_currency_precision_when_applying_rate() {
        assertThat(Money.of("826.44").times(Rate.ofPercent(16))).isEqualTo(Money.of("132.23"));
        assertThat(Money.of("0.50").times(Rate.ofPercent(3))).isEqualTo(Money.of("0.02"));
        assertThat(Money.of("47760").times(Rate.ofPercent(3))).isEqualTo(Money.of("1433"));
    }

    @Test
    void should_create_zero_with_currency_precision() {
        assertThat(Money.zero(2)).isEqualTo(Money.of("0.00"));
        assertThat(Money.zero(0)).isEqualTo(Money.of("0"));
    }

    @Test
    void should_reject_null_amount() {
        assertThatNullPointerException().isThrownBy(() -> new Money(null));
    }
}
