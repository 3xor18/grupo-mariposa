package com.grupomariposa.orders.domain.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record MarketCatalog(List<MarketDefinition> markets, CurrencyCatalog currencies) {

    private static final String EMPTY = "At least one market must be configured";
    private static final String DUPLICATED = "Market %s is configured more than once";
    private static final String UNKNOWN_CURRENCY = "Market %s uses currency %s outside the catalog";
    private static final String UNKNOWN_MARKET = "Market %s is not in the market catalog";

    public MarketCatalog {
        Objects.requireNonNull(markets, "markets");
        Objects.requireNonNull(currencies, "currencies");
        if (markets.isEmpty()) {
            throw new IllegalArgumentException(EMPTY);
        }
        markets = List.copyOf(markets);
        index(markets, currencies);
    }

    public boolean supports(final MarketCode market) {
        return definitionOf(market).isPresent();
    }

    public Optional<CurrencyCode> currencyOf(final MarketCode market) {
        return definitionOf(market).map(MarketDefinition::currency);
    }

    public boolean accepts(final MarketCode market, final CurrencyCode currency) {
        return currencyOf(market).filter(currency::equals).isPresent();
    }

    public int fractionDigitsOf(final MarketCode market) {
        return currencies.fractionDigitsOf(currencyOf(market).orElseThrow(() ->
                new IllegalArgumentException(UNKNOWN_MARKET.formatted(market))));
    }

    public List<MarketCode> supportedMarkets() {
        return markets.stream().map(MarketDefinition::code).toList();
    }

    private Optional<MarketDefinition> definitionOf(final MarketCode market) {
        return markets.stream().filter(definition -> definition.code().equals(market))
                .findFirst();
    }

    private static Map<MarketCode, MarketDefinition> index(
            final List<MarketDefinition> markets, final CurrencyCatalog currencies) {
        final Map<MarketCode, MarketDefinition> byCode = new LinkedHashMap<>();
        for (final MarketDefinition definition : markets) {
            if (byCode.put(definition.code(), definition) != null) {
                throw new IllegalArgumentException(DUPLICATED.formatted(definition.code()));
            }
            if (!currencies.supports(definition.currency())) {
                throw new IllegalArgumentException(UNKNOWN_CURRENCY.formatted(definition.code(),
                        definition.currency()));
            }
        }
        return Collections.unmodifiableMap(byCode);
    }
}
