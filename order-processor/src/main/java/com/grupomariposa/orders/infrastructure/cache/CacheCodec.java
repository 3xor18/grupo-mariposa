package com.grupomariposa.orders.infrastructure.cache;

import java.util.Optional;

public interface CacheCodec<T> {

    String encode(T value);

    Optional<T> decode(String payload);
}
