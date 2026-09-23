package com.grupomariposa.orders.domain.model;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

public record MarketCode(String value) {

    private static final Pattern FORMAT = Pattern.compile("^[A-Z]{2}$");
    private static final String INVALID = "Market code must be two upper-case letters";

    public MarketCode {
        Objects.requireNonNull(value, "value");
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException(INVALID);
        }
    }

    public static Optional<MarketCode> parse(final String value) {
        return value != null && FORMAT.matcher(value).matches()
                ? Optional.of(new MarketCode(value)) : Optional.empty();
    }

    @Override
    public String toString() {
        return value;
    }
}
