package com.grupomariposa.orders.domain.model;

import static com.grupomariposa.orders.domain.DomainFixtures.market;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.grupomariposa.orders.domain.Currencies;
import com.grupomariposa.orders.domain.DomainFixtures;
import com.grupomariposa.orders.domain.Markets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class MarketTest {

    private final MarketCatalog markets = DomainFixtures.MARKETS;

    @ParameterizedTest(name = "{0} uses {1} with {2} decimals")
    @CsvSource({"MX, MXN, 2", "CO, COP, 2", "PE, PEN, 2", "CL, CLP, 0", "EC, USD, 2"})
    void should_resolve_currency_and_precision_per_market(final MarketCode market,
                                                         final CurrencyCode currency,
                                                         final int digits) {
        assertThat(markets.currencyOf(market)).contains(currency);
        assertThat(markets.accepts(market, currency)).isTrue();
        assertThat(markets.accepts(market, new CurrencyCode("XXX"))).isFalse();
        assertThat(markets.fractionDigitsOf(market)).isEqualTo(digits);
    }

    @Test
    void should_share_a_currency_between_markets() {
        final MarketCatalog dollarized = new MarketCatalog(List.of(
                market(Markets.EC, Currencies.USD, "es-EC"),
                market(new MarketCode("PA"), Currencies.USD, "es-PA")), DomainFixtures.CURRENCIES);

        assertThat(dollarized.accepts(new MarketCode("PA"), Currencies.USD)).isTrue();
        assertThat(dollarized.supportedMarkets()).containsExactly(Markets.EC,
                new MarketCode("PA"));
    }

    @Test
    void should_reject_invalid_catalogs() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                new MarketCatalog(List.of(), DomainFixtures.CURRENCIES));
        assertThatIllegalArgumentException().isThrownBy(() -> new MarketCatalog(List.of(
                market(Markets.MX, Currencies.MXN, "es-MX"),
                market(Markets.MX, Currencies.MXN, "es-MX")), DomainFixtures.CURRENCIES));
        assertThatIllegalArgumentException().isThrownBy(() -> new MarketCatalog(List.of(
                market(Markets.MX, new CurrencyCode("ARS"), "es-AR")),
                DomainFixtures.CURRENCIES));
        assertThatIllegalArgumentException().isThrownBy(() -> markets.fractionDigitsOf(
                new MarketCode("AR")));
    }

    @Test
    void should_reject_invalid_currency_catalogs() {
        assertThatIllegalArgumentException().isThrownBy(() -> new CurrencyCatalog(Map.of()));
        assertThatIllegalArgumentException().isThrownBy(() ->
                new CurrencyCatalog(Map.of(Currencies.USD, 5)));
        assertThatIllegalArgumentException().isThrownBy(() ->
                new CurrencyCatalog(Map.of(Currencies.USD, -1)));
        assertThatIllegalArgumentException().isThrownBy(() ->
                DomainFixtures.CURRENCIES.fractionDigitsOf(new CurrencyCode("ARS")));
    }

    @Test
    void should_parse_well_formed_codes() {
        assertThat(MarketCode.parse("CO")).contains(Markets.CO);
        assertThat(CurrencyCode.parse("CLP")).contains(Currencies.CLP);
        assertThat(Markets.CL).hasToString("CL");
        assertThat(Currencies.USD).hasToString("USD");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"mx", " MX", "MEX", "M1"})
    void should_not_parse_malformed_market_codes(final String code) {
        assertThat(MarketCode.parse(code)).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"usd", "US", "USDD", "U1D"})
    void should_not_parse_malformed_currency_codes(final String code) {
        assertThat(CurrencyCode.parse(code)).isEmpty();
    }

    @Test
    void should_reject_malformed_codes_on_construction() {
        assertThatIllegalArgumentException().isThrownBy(() -> new MarketCode("mex"));
        assertThatIllegalArgumentException().isThrownBy(() -> new CurrencyCode("US"));
    }
}
