package com.grupomariposa.orders.infrastructure.kafka.masterdata;

public final class MalformedChangeEvent extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public MalformedChangeEvent(final String message) {
        super(message);
    }
}
