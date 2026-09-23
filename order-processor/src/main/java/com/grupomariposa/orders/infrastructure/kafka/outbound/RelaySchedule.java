package com.grupomariposa.orders.infrastructure.kafka.outbound;

import java.time.Duration;
import java.util.Objects;

public record RelaySchedule(Duration interval) {

    public static final String BEAN_NAME = "outboxRelaySchedule";

    public RelaySchedule {
        Objects.requireNonNull(interval, "interval");
    }

    public long intervalMillis() {
        return interval.toMillis();
    }
}
