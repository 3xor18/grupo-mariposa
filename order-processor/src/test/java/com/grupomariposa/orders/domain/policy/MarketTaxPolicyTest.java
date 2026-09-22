package com.grupomariposa.orders.domain.policy;

import static com.grupomariposa.orders.domain.DomainFixtures.client;
import static com.grupomariposa.orders.domain.DomainFixtures.wholesaleClient;
import static org.assertj.core.api.Assertions.assertThat;

import com.grupomariposa.orders.domain.model.ClientSegment;
import com.grupomariposa.orders.domain.model.ClientStatus;
import com.grupomariposa.orders.domain.model.Market;
import com.grupomariposa.orders.domain.model.Rate;
import com.grupomariposa.orders.domain.model.TaxCategory;
import com.grupomariposa.orders.domain.model.TaxRegime;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

class MarketTaxPolicyTest {

    private final TaxPolicy policy = new MarketTaxPolicy();

    @ParameterizedTest(name = "{0} {1} -> {2}%")
    @CsvSource({
        "MX, STANDARD, 16", "MX, REDUCED, 8", "MX, EXEMPT, 0",
        "CO, STANDARD, 19", "CO, REDUCED, 5", "CO, EXEMPT, 0",
        "PE, STANDARD, 18", "PE, REDUCED, 10", "PE, EXEMPT, 0"
    })
    void should_apply_market_rate_for_category(final Market market, final TaxCategory category,
                                               final int percent) {
        assertThat(policy.rateFor(market, wholesaleClient(market), category))
                .isEqualTo(Rate.ofPercent(percent));
    }

    @ParameterizedTest
    @EnumSource(Market.class)
    void should_not_tax_exempt_regime_clients(final Market market) {
        final var exempt = client(market, ClientSegment.RETAIL, TaxRegime.EXEMPT,
                ClientStatus.ACTIVE);

        for (final TaxCategory category : TaxCategory.values()) {
            assertThat(policy.rateFor(market, exempt, category).isZero()).isTrue();
        }
    }

    @ParameterizedTest
    @EnumSource(value = TaxRegime.class, names = {"GENERAL", "SIMPLIFIED"})
    void should_tax_non_exempt_regimes(final TaxRegime regime) {
        final var taxed = client(Market.MX, ClientSegment.RETAIL, regime, ClientStatus.ACTIVE);

        assertThat(policy.rateFor(Market.MX, taxed, TaxCategory.STANDARD))
                .isEqualTo(Rate.ofPercent(16));
    }
}
