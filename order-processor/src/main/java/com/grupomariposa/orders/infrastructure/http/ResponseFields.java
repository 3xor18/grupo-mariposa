package com.grupomariposa.orders.infrastructure.http;

import com.grupomariposa.orders.application.error.ExternalPermanentException;
import java.util.Arrays;
import java.util.Objects;

public final class ResponseFields {

    private static final String INVALID_FIELD = "%s returned an invalid %s";
    private static final String MISSING_FIELD = "%s returned a response without %s";

    private final Dependency dependency;

    public ResponseFields(final Dependency dependency) {
        this.dependency = Objects.requireNonNull(dependency, "dependency");
    }

    public String requireText(final String field, final String value) {
        if (value == null || value.isBlank()) {
            throw failure(MISSING_FIELD, field);
        }
        return value;
    }

    public <E extends Enum<E>> E parse(final Class<E> type, final String field,
                                       final String value) {
        return Arrays.stream(type.getEnumConstants())
                .filter(constant -> constant.name().equals(value))
                .findFirst()
                .orElseThrow(() -> invalid(field));
    }

    public ExternalPermanentException invalid(final String field) {
        return failure(INVALID_FIELD, field);
    }

    private ExternalPermanentException failure(final String template, final String field) {
        return new ExternalPermanentException(dependency.id(),
                template.formatted(dependency.id(), field), null);
    }
}
