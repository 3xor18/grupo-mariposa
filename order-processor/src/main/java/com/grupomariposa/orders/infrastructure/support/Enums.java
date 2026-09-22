package com.grupomariposa.orders.infrastructure.support;

import java.util.function.Function;

public final class Enums {

    private Enums() {
    }

    public static String nameOf(final Enum<?> value) {
        return value == null ? null : value.name();
    }

    public static <T> T parseNullable(final String value, final Function<String, T> parser) {
        return value == null ? null : parser.apply(value);
    }
}
