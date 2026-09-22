package com.grupomariposa.orders.application.service;

import java.time.Duration;
import java.util.Objects;

public record RelaySettings(int batchSize, Duration lease, Duration sendTimeout,
                           Duration initialBackoff, Duration maxBackoff, String owner) {

    private static final int MAX_BACKOFF_EXPONENT = 20;

    public RelaySettings {
        Objects.requireNonNull(lease, "lease");
        Objects.requireNonNull(sendTimeout, "sendTimeout");
        Objects.requireNonNull(initialBackoff, "initialBackoff");
        Objects.requireNonNull(maxBackoff, "maxBackoff");
        Objects.requireNonNull(owner, "owner");
        if (batchSize < 1) {
            throw new IllegalArgumentException("Batch size must be positive");
        }
    }

    public Duration backoffFor(final int attempts) {
        final int exponent = Math.min(Math.max(attempts - 1, 0), MAX_BACKOFF_EXPONENT);
        final Duration candidate = initialBackoff.multipliedBy(1L << exponent);
        return candidate.compareTo(maxBackoff) > 0 ? maxBackoff : candidate;
    }
}
