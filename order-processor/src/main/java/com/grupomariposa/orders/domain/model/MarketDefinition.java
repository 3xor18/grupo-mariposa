package com.grupomariposa.orders.domain.model;

import java.util.Locale;
import java.util.Objects;

public record MarketDefinition(MarketCode code, CurrencyCode currency, Locale locale) {

    public MarketDefinition {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(locale, "locale");
    }
}
