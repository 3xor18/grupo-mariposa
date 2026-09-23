package com.grupomariposa.orders.domain.model;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record TaxRateTable(Map<MarketCode, Map<TaxCategory, Rate>> rates) {

    private static final String EMPTY = "The tax rate table needs at least one market";
    private static final String INCOMPLETE = "Market %s needs a tax rate for every category";
    private static final String NOT_A_FRACTION = "Tax rate for %s %s must be between 0 and 1";
    private static final String UNKNOWN_MARKET = "No tax rates configured for market %s";

    public TaxRateTable {
        Objects.requireNonNull(rates, "rates");
        if (rates.isEmpty()) {
            throw new IllegalArgumentException(EMPTY);
        }
        final Map<MarketCode, Map<TaxCategory, Rate>> copy = new LinkedHashMap<>();
        rates.forEach((market, categories) -> copy.put(market, validated(market, categories)));
        rates = Collections.unmodifiableMap(copy);
    }

    public Rate rateFor(final MarketCode market, final TaxCategory category) {
        final Map<TaxCategory, Rate> categories = rates.get(market);
        if (categories == null) {
            throw new IllegalArgumentException(UNKNOWN_MARKET.formatted(market));
        }
        return categories.get(category);
    }

    public boolean covers(final Collection<MarketCode> markets) {
        return rates.keySet().containsAll(markets);
    }

    private static Map<TaxCategory, Rate> validated(final MarketCode market,
                                                    final Map<TaxCategory, Rate> categories) {
        if (categories == null || !categories.keySet().containsAll(
                EnumSet.allOf(TaxCategory.class))) {
            throw new IllegalArgumentException(INCOMPLETE.formatted(market));
        }
        categories.forEach((category, rate) -> {
            if (rate == null || !rate.isFraction()) {
                throw new IllegalArgumentException(NOT_A_FRACTION.formatted(market, category));
            }
        });
        return Collections.unmodifiableMap(new EnumMap<>(categories));
    }
}
