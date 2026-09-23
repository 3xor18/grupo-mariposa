package com.grupomariposa.orders.infrastructure.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

public final class JsonCacheCodec<T, S> implements CacheCodec<T> {

    private final ObjectMapper objectMapper;
    private final Class<S> storedType;
    private final Function<T, S> toStored;
    private final Function<S, Optional<T>> fromStored;

    public JsonCacheCodec(final ObjectMapper objectMapper, final Class<S> storedType,
                          final Function<T, S> toStored,
                          final Function<S, Optional<T>> fromStored) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.storedType = Objects.requireNonNull(storedType, "storedType");
        this.toStored = Objects.requireNonNull(toStored, "toStored");
        this.fromStored = Objects.requireNonNull(fromStored, "fromStored");
    }

    @Override
    public String encode(final T value) {
        try {
            return objectMapper.writeValueAsString(toStored.apply(value));
        } catch (JsonProcessingException invalid) {
            throw new CacheCodecException(invalid);
        }
    }

    @Override
    public Optional<T> decode(final String payload) {
        try {
            return fromStored.apply(objectMapper.readValue(payload, storedType));
        } catch (JsonProcessingException invalid) {
            throw new CacheCodecException(invalid);
        }
    }
}
