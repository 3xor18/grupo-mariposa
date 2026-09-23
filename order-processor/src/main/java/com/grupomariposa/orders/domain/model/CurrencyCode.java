package com.grupomariposa.orders.domain.model;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

public record CurrencyCode(String value) {

    private static final Pattern FORMAT = Pattern.compile("^[A-Z]{3}$");
    private static final String INVALID = "Currency code must be three upper-case letters";

    public CurrencyCode {
        Objects.requireNonNull(value, "value");
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException(INVALID);
        }
    }

    public static Optional<CurrencyCode> parse(final String value) {
        return value != null && FORMAT.matcher(value).matches()
                ? Optional.of(new CurrencyCode(value)) : Optional.empty();
    }

    @Override
    public String toString() {
        return value;
    }
}
