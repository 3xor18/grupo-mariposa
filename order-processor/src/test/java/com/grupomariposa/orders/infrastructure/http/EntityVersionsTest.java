package com.grupomariposa.orders.infrastructure.http;

import static org.assertj.core.api.Assertions.assertThat;

import com.grupomariposa.orders.infrastructure.masterdata.Versioned;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpHeaders;

class EntityVersionsTest {

    @Test
    void should_prefer_positive_body_version() {
        assertThat(EntityVersions.of(5L, etag("\"9\""))).isEqualTo(5L);
    }

    @ParameterizedTest(name = "etag {0} -> {1}")
    @CsvSource(delimiter = '|', value = {
            "\"9\"|9", "W/\"10\"|10", "11|11", "\"abc\"|0", "\"-1\"|0", "' \"4\" '|4"})
    void should_fall_back_to_numeric_etag(final String etag, final long expected) {
        assertThat(EntityVersions.of(null, etag(etag))).isEqualTo(expected);
        assertThat(EntityVersions.of(0L, etag(etag))).isEqualTo(expected);
    }

    @Test
    void should_default_to_unknown_version_without_etag() {
        assertThat(EntityVersions.of(null, new HttpHeaders()))
                .isEqualTo(Versioned.UNKNOWN_VERSION);
    }

    private static HttpHeaders etag(final String value) {
        final HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.ETAG, value);
        return headers;
    }
}
