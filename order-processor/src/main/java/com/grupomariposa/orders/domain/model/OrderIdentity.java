package com.grupomariposa.orders.domain.model;

import java.util.Objects;

public record OrderIdentity(String orderId, String sourceEventId, int eventVersion) {

    private static final String INVALID_VERSION = "Event version must be at least one";

    public OrderIdentity {
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(sourceEventId, "sourceEventId");
        if (eventVersion < 1) {
            throw new IllegalArgumentException(INVALID_VERSION);
        }
    }
}
