package com.grupomariposa.orders.application.validation;

import java.util.ArrayList;
import java.util.List;

final class ErrorCollector {

    private static final String REQUIRED = "is required";
    private static final String TOO_LONG = "must have at most %d characters";

    private final List<ValidationError> errors = new ArrayList<>();

    void add(final String field, final String message) {
        errors.add(new ValidationError(field, message));
    }

    boolean requirePresent(final String field, final Object value) {
        if (value == null) {
            add(field, REQUIRED);
            return false;
        }
        return true;
    }

    void requireText(final String field, final String value, final int maxLength) {
        if (value == null || value.isBlank()) {
            add(field, REQUIRED);
        } else {
            limitLength(field, value, maxLength);
        }
    }

    void limitLength(final String field, final String value, final int maxLength) {
        if (value != null && value.length() > maxLength) {
            add(field, TOO_LONG.formatted(maxLength));
        }
    }

    boolean isEmpty() {
        return errors.isEmpty();
    }

    List<ValidationError> errors() {
        return List.copyOf(errors);
    }
}
