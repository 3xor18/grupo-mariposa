package com.grupomariposa.orders.domain.model;

import java.util.Objects;
import java.util.Optional;

public sealed interface Lookup<T> permits Lookup.Found, Lookup.NotFound {

    static <T> Lookup<T> found(final T value) {
        return new Found<>(value);
    }

    static <T> Lookup<T> notFound() {
        return new NotFound<>();
    }

    Optional<T> value();

    record Found<T>(T resource) implements Lookup<T> {

        public Found {
            Objects.requireNonNull(resource, "resource");
        }

        @Override
        public Optional<T> value() {
            return Optional.of(resource);
        }
    }

    record NotFound<T>() implements Lookup<T> {

        @Override
        public Optional<T> value() {
            return Optional.empty();
        }
    }
}
