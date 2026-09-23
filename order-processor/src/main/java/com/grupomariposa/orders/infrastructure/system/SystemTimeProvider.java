package com.grupomariposa.orders.infrastructure.system;

import com.grupomariposa.orders.application.port.out.TimeProvider;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

public final class SystemTimeProvider implements TimeProvider {

    private final Clock clock;

    public SystemTimeProvider(final Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public Instant now() {
        return clock.instant();
    }
}
