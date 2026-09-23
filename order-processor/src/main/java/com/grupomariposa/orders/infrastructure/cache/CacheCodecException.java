package com.grupomariposa.orders.infrastructure.cache;

public final class CacheCodecException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public CacheCodecException(final Exception cause) {
        super(cause);
    }
}
