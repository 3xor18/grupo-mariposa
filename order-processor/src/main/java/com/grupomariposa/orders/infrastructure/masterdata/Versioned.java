package com.grupomariposa.orders.infrastructure.masterdata;

import java.util.Objects;

public record Versioned<T>(T value, long version) {

    public static final long UNKNOWN_VERSION = 0L;
    private static final String NEGATIVE = "Version cannot be negative";

    public Versioned {
        Objects.requireNonNull(value, "value");
        if (version < UNKNOWN_VERSION) {
            throw new IllegalArgumentException(NEGATIVE);
        }
    }
}
