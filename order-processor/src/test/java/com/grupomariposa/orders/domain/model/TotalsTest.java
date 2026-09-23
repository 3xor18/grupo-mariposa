package com.grupomariposa.orders.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class TotalsTest {

    @Test
    void should_sum_already_rounded_line_amounts() {
        final LineAmounts first = line("0.33", "0.01", "0.32", "0.05", "0.37");
        final LineAmounts second = line("0.33", "0.01", "0.32", "0.05", "0.37");

        assertThat(Totals.sumOf(List.of(first, second))).isEqualTo(new Totals(
                Money.of("0.66"), Money.of("0.02"), Money.of("0.64"), Money.of("0.10"),
                Money.of("0.74")));
    }

    @Test
    void should_be_zero_without_lines() {
        assertThat(Totals.sumOf(List.of())).isEqualTo(Totals.ZERO);
    }

    private static LineAmounts line(final String gross, final String discount, final String net,
                                    final String tax, final String total) {
        return new LineAmounts(Money.of(gross), Rate.ofPercent(3), Money.of(discount),
                Money.of(net), Rate.ofPercent(16), Money.of(tax), Money.of(total));
    }
}
