package com.grupomariposa.orders.domain.policy;

import static com.grupomariposa.orders.domain.DomainFixtures.WHOLESALE_DISCOUNT;
import static com.grupomariposa.orders.domain.DomainFixtures.retailClient;
import static com.grupomariposa.orders.domain.DomainFixtures.wholesaleClient;
import static org.assertj.core.api.Assertions.assertThat;

import com.grupomariposa.orders.domain.Markets;
import com.grupomariposa.orders.domain.model.DiscountRule;
import com.grupomariposa.orders.domain.model.Rate;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class WholesaleVolumeDiscountPolicyTest {

    private final DiscountPolicy policy = new WholesaleVolumeDiscountPolicy(WHOLESALE_DISCOUNT);

    @ParameterizedTest(name = "wholesale qty {0} -> {1}%")
    @CsvSource({"1, 0", "19, 0", "20, 3", "21, 3", "500, 3"})
    void should_discount_wholesale_from_threshold(final int quantity, final int percent) {
        assertThat(policy.rateFor(wholesaleClient(Markets.MX), quantity))
                .isEqualTo(Rate.ofPercent(percent));
    }

    @ParameterizedTest(name = "configured rule {0}% from {1}: qty {2} -> {3}%")
    @CsvSource({"5, 10, 9, 0", "5, 10, 10, 5", "0, 1, 50, 0"})
    void should_apply_configured_rule(final int percent, final int threshold,
                                      final int quantity, final int expected) {
        final DiscountPolicy configured = new WholesaleVolumeDiscountPolicy(
                new DiscountRule(Rate.ofPercent(percent), threshold));

        assertThat(configured.rateFor(wholesaleClient(Markets.PE), quantity))
                .isEqualTo(Rate.ofPercent(expected));
    }

    @ParameterizedTest(name = "retail qty {0} -> no discount")
    @ValueSource(ints = {1, 19, 20, 21, 1000})
    void should_never_discount_retail(final int quantity) {
        assertThat(policy.rateFor(retailClient(Markets.CO), quantity).value()).isZero();
    }
}
