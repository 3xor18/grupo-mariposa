package com.grupomariposa.orders.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class MarketTest {

    @ParameterizedTest
    @CsvSource({"MX, MXN", "CO, COP", "PE, PEN"})
    void should_accept_only_its_own_currency(final Market market, final Currency currency) {
        assertThat(market.currency()).isEqualTo(currency);
        assertThat(market.acceptsCurrency(currency)).isTrue();
        for (final Currency other : Currency.values()) {
            assertThat(market.acceptsCurrency(other)).isEqualTo(other == currency);
        }
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
