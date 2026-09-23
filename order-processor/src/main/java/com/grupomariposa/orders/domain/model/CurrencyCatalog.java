package com.grupomariposa.orders.domain.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record CurrencyCatalog(Map<CurrencyCode, Integer> fractionDigits) {

    public static final int MAX_FRACTION_DIGITS = 4;
    private static final String EMPTY = "At least one currency must be configured";
    private static final String INVALID_DIGITS = "Currency %s must have between 0 and %d decimals";
    private static final String UNKNOWN = "Currency %s is not in the currency catalog";

    public CurrencyCatalog {
        Objects.requireNonNull(fractionDigits, "fractionDigits");
        if (fractionDigits.isEmpty()) {
            throw new IllegalArgumentException(EMPTY);
        }
        fractionDigits.forEach((currency, digits) -> {
            if (digits == null || digits < 0 || digits > MAX_FRACTION_DIGITS) {
                throw new IllegalArgumentException(
                        INVALID_DIGITS.formatted(currency, MAX_FRACTION_DIGITS));
            }
        });
        fractionDigits = Collections.unmodifiableMap(new LinkedHashMap<>(fractionDigits));
    }

    public boolean supports(final CurrencyCode currency) {
        return fractionDigits.containsKey(currency);
    }

    public int fractionDigitsOf(final CurrencyCode currency) {
        final Integer digits = fractionDigits.get(currency);
        if (digits == null) {
            throw new IllegalArgumentException(UNKNOWN.formatted(currency));
        }
        return digits;
    }
}
