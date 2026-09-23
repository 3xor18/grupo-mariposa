package com.grupomariposa.orders.infrastructure.cache;

import java.util.Locale;

public enum CacheOperation {
    READ,
    WRITE,
    EVENT;

    public String tagValue() {
        return name().toLowerCase(Locale.ROOT);
    }
}
