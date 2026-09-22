package com.grupomariposa.orders.application.port.out;

import java.time.Instant;
import java.util.Objects;

public record InboxEntry(String eventId, String orderId, int eventVersion, InboxOutcome outcome,
                         Instant receivedAt) {

    public InboxEntry {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(receivedAt, "receivedAt");
    }
}
