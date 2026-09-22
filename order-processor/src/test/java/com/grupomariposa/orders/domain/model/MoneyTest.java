package com.grupomariposa.orders.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MoneyTest {

    @ParameterizedTest(name = "{0} rounds to {1}")
    @CsvSource({
        "1.005, 1.01",
        "2.675, 2.68",
        "0.125, 0.13",
        "10.0049, 10.00",
        "132.2304, 132.23",
        "0.004, 0.00",
        "-1.005, -1.01",
        "7, 7.00"
    })
    void should_round_half_up_to_two_decimals_when_created(final String raw,
                                                           final String expected) {
        assertThat(Money.of(raw).amount()).isEqualByComparingTo(expected)
                .hasScaleOf(Money.SCALE);
    }

    @Test
    void should_multiply_unit_price_by_quantity_and_round_once() {
        assertThat(Money.ofUnits(new BigDecimal("0.335"), 3)).isEqualTo(Money.of("1.01"));
    }

    @Test
    void should_add_and_subtract_amounts() {
        final Money base = Money.of("10.10");

        assertThat(base.plus(Money.of("0.05"))).isEqualTo(Money.of("10.15"));
        assertThat(base.minus(Money.of("0.15"))).isEqualTo(Money.of("9.95"));
    }

    @Test
    void should_round_half_up_when_applying_rate() {
        assertThat(Money.of("826.44").times(Rate.ofPercent(16))).isEqualTo(Money.of("132.23"));
        assertThat(Money.of("0.50").times(Rate.ofPercent(3))).isEqualTo(Money.of("0.02"));
    }

    @Test
    void should_compare_equal_regardless_of_input_scale() {
        assertThat(Money.of(new BigDecimal("5"))).isEqualTo(Money.of("5.000"));
        assertThat(Money.ZERO.amount()).isEqualByComparingTo("0.00");
    }

    @Test
    void should_reject_null_amount() {
        assertThatNullPointerException().isThrownBy(() -> Money.of((BigDecimal) null));
    }
}
