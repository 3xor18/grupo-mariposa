package com.grupomariposa.orders.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.grupomariposa.orders.domain.DomainFixtures;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class MarketTest {

    private final MarketCurrencies markets = DomainFixtures.MARKETS;

    @ParameterizedTest
    @CsvSource({"MX, MXN", "CO, COP", "PE, PEN"})
    void should_accept_only_configured_currency(final Market market, final Currency currency) {
        assertThat(markets.currencyOf(market)).contains(currency);
        for (final Currency other : Currency.values()) {
            assertThat(markets.accepts(market, other)).isEqualTo(other == currency);
        }
    }

    @Test
    void should_expose_supported_markets_in_declaration_order() {
        final MarketCurrencies subset = new MarketCurrencies(Map.of(Market.PE, Currency.PEN,
                Market.MX, Currency.MXN));

        assertThat(subset.supportedMarkets()).containsExactly(Market.MX, Market.PE);
        assertThat(subset.supports(Market.CO)).isFalse();
        assertThat(subset.currencyOf(Market.CO)).isEmpty();
    }

    @Test
    void should_reject_empty_market_configuration() {
        assertThatIllegalArgumentException().isThrownBy(() -> new MarketCurrencies(Map.of()));
    }

    @Test
    void should_resolve_known_codes() {
        assertThat(Market.fromCode("CO")).contains(Market.CO);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"AR", "mx", " MX"})
    void should_not_resolve_unknown_codes(final String code) {
        assertThat(Market.fromCode(code)).isEmpty();
    }
}
