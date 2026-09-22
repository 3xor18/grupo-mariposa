package com.grupomariposa.orders.application.command;

import java.time.Instant;
import java.util.Objects;

public record Reception(Instant receivedAt, String traceId) {

    public Reception {
        Objects.requireNonNull(receivedAt, "receivedAt");
    }
}
