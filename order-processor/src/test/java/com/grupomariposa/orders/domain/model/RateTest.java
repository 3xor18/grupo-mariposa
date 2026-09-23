package com.grupomariposa.orders.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class RateTest {

    @Test
    void should_convert_percent_to_fraction() {
        assertThat(Rate.ofPercent(16).value()).isEqualByComparingTo("0.16");
        assertThat(Rate.ofPercent(3).value()).isEqualByComparingTo("0.03");
    }

    @Test
    void should_report_zero() {
        assertThat(Rate.ZERO.value()).isZero();
        assertThat(Rate.ofPercent(1).value()).isPositive();
    }

    @Test
    void should_reject_negative_rate() {
        assertThatIllegalArgumentException().isThrownBy(() -> new Rate(new BigDecimal("-0.01")));
    }
}
