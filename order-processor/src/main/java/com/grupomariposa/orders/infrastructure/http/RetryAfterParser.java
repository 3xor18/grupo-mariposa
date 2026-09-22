package com.grupomariposa.orders.infrastructure.http;

import java.time.Clock;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Objects;
import java.util.Optional;

public final class RetryAfterParser {

    private static final String DIGITS = "\\d+";
    private static final int MAX_SAFE_DIGITS = 18;
    private static final Duration LONGEST = Duration.ofSeconds(Long.MAX_VALUE);

    private final Clock clock;

    public RetryAfterParser(final Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public Optional<Duration> parse(final String header) {
        if (header == null || header.isBlank()) {
            return Optional.empty();
        }
        final String value = header.trim();
        if (value.matches(DIGITS)) {
            return Optional.of(value.length() > MAX_SAFE_DIGITS
                    ? LONGEST : Duration.ofSeconds(Long.parseLong(value)));
        }
        return parseDate(value);
    }

    private Optional<Duration> parseDate(final String value) {
        try {
            final ZonedDateTime at =
                    ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME);
            final Duration wait = Duration.between(clock.instant(), at.toInstant());
            return Optional.of(wait.isNegative() ? Duration.ZERO : wait);
        } catch (DateTimeParseException invalid) {
            return Optional.empty();
        }
    }
}
