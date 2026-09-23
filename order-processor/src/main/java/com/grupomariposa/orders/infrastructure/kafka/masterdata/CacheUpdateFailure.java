package com.grupomariposa.orders.infrastructure.kafka.masterdata;

public final class CacheUpdateFailure extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public CacheUpdateFailure(final String message) {
        super(message);
    }
}
