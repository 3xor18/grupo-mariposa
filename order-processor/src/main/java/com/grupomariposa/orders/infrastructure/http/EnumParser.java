package com.grupomariposa.orders.infrastructure.http;

import com.grupomariposa.orders.application.error.ExternalPermanentException;
import java.util.Arrays;
import java.util.Objects;

public final class EnumParser {

    private static final String INVALID_FIELD = "%s returned an invalid %s";

    private final Dependency dependency;

    public EnumParser(final Dependency dependency) {
        this.dependency = Objects.requireNonNull(dependency, "dependency");
    }

    public <E extends Enum<E>> E parse(final Class<E> type, final String field,
                                       final String value) {
        return Arrays.stream(type.getEnumConstants())
                .filter(constant -> constant.name().equals(value))
                .findFirst()
                .orElseThrow(() -> invalid(field));
    }

    public ExternalPermanentException invalid(final String field) {
        return new ExternalPermanentException(dependency.id(),
                INVALID_FIELD.formatted(dependency.id(), field), null);
    }
}
