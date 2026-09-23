package com.grupomariposa.orders.domain.model;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

public sealed interface Lookup<T> permits Lookup.Found, Lookup.NotFound {

    static <T> Lookup<T> found(final T value) {
        return new Found<>(value);
    }

    static <T> Lookup<T> notFound() {
        return new NotFound<>();
    }

    Optional<T> value();

    <R> Lookup<R> map(Function<? super T, ? extends R> mapper);

    record Found<T>(T resource) implements Lookup<T> {

        public Found {
            Objects.requireNonNull(resource, "resource");
        }

        @Override
        public Optional<T> value() {
            return Optional.of(resource);
        }

        @Override
        public <R> Lookup<R> map(final Function<? super T, ? extends R> mapper) {
            return new Found<>(mapper.apply(resource));
        }
    }

    record NotFound<T>() implements Lookup<T> {

        @Override
        public Optional<T> value() {
            return Optional.empty();
        }

        @Override
        public <R> Lookup<R> map(final Function<? super T, ? extends R> mapper) {
            return new NotFound<>();
        }
    }
}
