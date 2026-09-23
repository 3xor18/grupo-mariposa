package com.grupomariposa.orders.application.validation;

import java.util.Objects;

public record ValidationError(String field, String message) {

    private static final String SEPARATOR = ": ";

    public ValidationError {
        Objects.requireNonNull(field, "field");
        Objects.requireNonNull(message, "message");
    }

    public String describe() {
        return field + SEPARATOR + message;
    }
}
