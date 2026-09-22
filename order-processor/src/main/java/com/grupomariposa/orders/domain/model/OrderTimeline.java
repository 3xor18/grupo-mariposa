package com.grupomariposa.orders.domain.model;

import java.time.Instant;
import java.util.Objects;

public record OrderTimeline(Instant occurredAt, Instant receivedAt, Instant processedAt) {

    public OrderTimeline {
        Objects.requireNonNull(receivedAt, "receivedAt");
        Objects.requireNonNull(processedAt, "processedAt");
    }
}
