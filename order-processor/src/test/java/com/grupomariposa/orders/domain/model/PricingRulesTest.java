package com.grupomariposa.orders.domain.model;

import static com.grupomariposa.orders.domain.DomainFixtures.rates;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.grupomariposa.orders.domain.Markets;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PricingRulesTest {

    @Test
    void should_look_up_configured_rates() {
        final TaxRateTable table = new TaxRateTable(Map.of(Markets.CO, rates(19, 5, 0)));

        assertThat(table.rateFor(Markets.CO, TaxCategory.REDUCED)).isEqualTo(Rate.ofPercent(5));
        assertThat(table.covers(List.of(Markets.CO))).isTrue();
        assertThat(table.covers(List.of(Markets.CO, Markets.PE))).isFalse();
    }

    @Test
    void should_reject_lookups_for_unconfigured_markets() {
        final TaxRateTable table = new TaxRateTable(Map.of(Markets.CO, rates(19, 5, 0)));

        assertThatIllegalArgumentException()
                .isThrownBy(() -> table.rateFor(Markets.MX, TaxCategory.STANDARD))
                .withMessage("No tax rates configured for market MX");
    }

    @Test
    void should_reject_empty_or_incomplete_tables() {
        final Map<TaxCategory, Rate> partial = new EnumMap<>(TaxCategory.class);
        partial.put(TaxCategory.STANDARD, Rate.ofPercent(16));
        final Map<MarketCode, Map<TaxCategory, Rate>> missingCategories = new HashMap<>();
        missingCategories.put(Markets.MX, partial);
        final Map<MarketCode, Map<TaxCategory, Rate>> nullCategories = new HashMap<>();
        nullCategories.put(Markets.MX, null);

        assertThatIllegalArgumentException().isThrownBy(() -> new TaxRateTable(Map.of()));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new TaxRateTable(missingCategories));
        assertThatIllegalArgumentException().isThrownBy(() -> new TaxRateTable(nullCategories));
    }

    @Test
    void should_reject_rates_above_one_or_missing() {
        final Map<TaxCategory, Rate> invalid = new EnumMap<>(rates(16, 8, 0));
        invalid.put(TaxCategory.STANDARD, new Rate(new BigDecimal("1.01")));
        final Map<TaxCategory, Rate> missing = new EnumMap<>(rates(16, 8, 0));
        missing.put(TaxCategory.EXEMPT, null);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new TaxRateTable(Map.of(Markets.MX, invalid)));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new TaxRateTable(Map.of(Markets.MX, missing)));
    }

    @ParameterizedTest
    @CsvSource({"19, false", "20, true", "21, true"})
    void should_apply_discount_rule_from_threshold(final int quantity, final boolean applies) {
        assertThat(new DiscountRule(Rate.ofPercent(3), 20).appliesTo(quantity))
                .isEqualTo(applies);
    }

    @Test
    void should_reject_invalid_discount_rules() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                new DiscountRule(new Rate(new BigDecimal("1.5")), 20));
        assertThatIllegalArgumentException().isThrownBy(() ->
                new DiscountRule(Rate.ofPercent(3), 0));
    }

    @Test
    void should_accept_full_rate_boundary() {
        assertThat(new Rate(BigDecimal.ONE).isFraction()).isTrue();
        assertThat(new DiscountRule(new Rate(BigDecimal.ONE), 1).rate().value())
                .isEqualByComparingTo("1");
    }
}
