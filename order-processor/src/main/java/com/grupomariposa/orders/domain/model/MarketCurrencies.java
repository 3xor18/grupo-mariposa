package com.grupomariposa.orders.domain.model;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public record MarketCurrencies(Map<Market, Currency> currencies) {

    private static final String EMPTY = "At least one market must be supported";

    public MarketCurrencies {
        Objects.requireNonNull(currencies, "currencies");
        if (currencies.isEmpty()) {
            throw new IllegalArgumentException(EMPTY);
        }
        currencies.values().forEach(currency -> Objects.requireNonNull(currency, "currency"));
        currencies = Collections.unmodifiableMap(new EnumMap<>(currencies));
    }

    public boolean supports(final Market market) {
        return currencies.containsKey(market);
    }

    public Optional<Currency> currencyOf(final Market market) {
        return Optional.ofNullable(currencies.get(market));
    }

    public boolean accepts(final Market market, final Currency currency) {
        return currency.equals(currencies.get(market));
    }

    public List<Market> supportedMarkets() {
        return List.copyOf(currencies.keySet());
    }

    public Set<Market> markets() {
        return currencies.keySet();
    }
}
