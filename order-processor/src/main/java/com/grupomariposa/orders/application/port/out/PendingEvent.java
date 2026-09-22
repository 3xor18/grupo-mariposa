package com.grupomariposa.orders.application.port.out;

import java.util.Objects;

public record PendingEvent(String eventId, String orderId, String topic, String key,
                           String payload, int attempts) {

    public PendingEvent {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(topic, "topic");
        Objects.requireNonNull(payload, "payload");
    }
}
