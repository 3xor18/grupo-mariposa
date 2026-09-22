package com.grupomariposa.orders.infrastructure.http;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class RetryAfterParserTest {

    private final RetryAfterParser parser = new RetryAfterParser(
            Clock.fixed(Instant.parse("2026-09-22T10:00:00Z"), ZoneOffset.UTC));

    @Test
    void should_parse_delta_seconds() {
        assertThat(parser.parse(" 3 ")).contains(Duration.ofSeconds(3));
    }

    @Test
    void should_parse_http_dates() {
        assertThat(parser.parse("Tue, 22 Sep 2026 10:00:05 GMT")).contains(Duration.ofSeconds(5));
        assertThat(parser.parse("Tue, 22 Sep 2026 09:00:00 GMT")).contains(Duration.ZERO);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"soon", "-1"})
    void should_ignore_missing_or_invalid_values(final String header) {
        assertThat(parser.parse(header)).isEmpty();
    }
}
