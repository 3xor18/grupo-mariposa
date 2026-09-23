package com.grupomariposa.orders.application.service;

import java.time.Duration;
import java.util.Objects;

public record RelaySettings(int batchSize, Duration lease, Duration sendTimeout,
                           Duration initialBackoff, Duration maxBackoff, String owner) {

    private static final int MAX_BACKOFF_EXPONENT = 20;
    private static final String INVALID_BATCH = "Batch size must be positive";
    private static final String NOT_POSITIVE = "Relay durations must be positive";
    private static final String LEASE_TOO_SHORT =
            "Outbox lease must be longer than the send timeout so a batch settles before expiry";
    private static final String BACKOFF_ORDER = "Initial backoff cannot exceed maximum backoff";
    private static final String BLANK_OWNER = "Relay owner cannot be blank";

    public RelaySettings {
        Objects.requireNonNull(lease, "lease");
        Objects.requireNonNull(sendTimeout, "sendTimeout");
        Objects.requireNonNull(initialBackoff, "initialBackoff");
        Objects.requireNonNull(maxBackoff, "maxBackoff");
        Objects.requireNonNull(owner, "owner");
        require(batchSize >= 1, INVALID_BATCH);
        require(isPositive(lease) && isPositive(sendTimeout) && isPositive(initialBackoff)
                && isPositive(maxBackoff), NOT_POSITIVE);
        require(lease.compareTo(sendTimeout) > 0, LEASE_TOO_SHORT);
        require(initialBackoff.compareTo(maxBackoff) <= 0, BACKOFF_ORDER);
        require(!owner.isBlank(), BLANK_OWNER);
    }

    public Duration backoffFor(final int attempts) {
        final int exponent = Math.min(Math.max(attempts - 1, 0), MAX_BACKOFF_EXPONENT);
        final Duration candidate = initialBackoff.multipliedBy(1L << exponent);
        return candidate.compareTo(maxBackoff) > 0 ? maxBackoff : candidate;
    }

    private static boolean isPositive(final Duration duration) {
        return !duration.isNegative() && !duration.isZero();
    }

    private static void require(final boolean condition, final String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
