package com.grupomariposa.orders.application.validation;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

final class ErrorCollector {

    private static final String REQUIRED = "is required";
    private static final String TOO_LONG = "must have at most %d characters";
    private static final String NO_MATCH = "must match %s";

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

    boolean requireText(final String field, final String value, final int maxLength) {
        if (value == null || value.isBlank()) {
            add(field, REQUIRED);
            return false;
        }
        return limitLength(field, value, maxLength);
    }

    void requireIdentifier(final String field, final String value, final int maxLength,
                           final Pattern pattern) {
        if (requireText(field, value, maxLength) && !pattern.matcher(value).matches()) {
            add(field, NO_MATCH.formatted(pattern.pattern()));
        }
    }

    boolean limitLength(final String field, final String value, final int maxLength) {
        if (value != null && value.length() > maxLength) {
            add(field, TOO_LONG.formatted(maxLength));
            return false;
        }
        return true;
    }

    boolean isEmpty() {
        return errors.isEmpty();
    }

    List<ValidationError> errors() {
        return List.copyOf(errors);
    }
}
