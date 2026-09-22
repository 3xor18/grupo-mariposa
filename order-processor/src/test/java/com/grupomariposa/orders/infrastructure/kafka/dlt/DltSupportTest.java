package com.grupomariposa.orders.infrastructure.kafka.dlt;

import com.grupomariposa.orders.infrastructure.observability.CauseSanitizer;
import static org.assertj.core.api.Assertions.assertThat;

import com.grupomariposa.orders.application.error.ErrorCategory;
import com.grupomariposa.orders.infrastructure.kafka.inbound.MessageIds;
import com.grupomariposa.orders.infrastructure.kafka.inbound.RecordProcessingFailure;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Headers;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.listener.ListenerExecutionFailedException;
import org.springframework.kafka.support.KafkaHeaders;

class DltSupportTest {

    private final CauseSanitizer sanitizer = new CauseSanitizer();
    private final DltHeadersFactory headers = new DltHeadersFactory(
            Clock.fixed(Instant.parse("2026-09-22T10:00:00Z"), ZoneOffset.UTC), sanitizer);

    @Test
    void should_strip_control_characters_secrets_and_emails() {
        assertThat(sanitizer.cause("line1\nline2\tBearer abc.def.ghi token=xyz mail a@b.com"))
                .isEqualTo("line1 line2 [redacted] [redacted] mail [redacted]");
    }

    @Test
    void should_describe_failures_without_leaking_secrets() {
        assertThat(sanitizer.describe(new IllegalStateException("password=hunter2")))
                .isEqualTo("IllegalStateException: [redacted]");
    }

    @Test
    void should_truncate_causes_and_identifiers() {
        assertThat(sanitizer.cause("x".repeat(400))).hasSize(CauseSanitizer.MAX_CAUSE_LENGTH);
        assertThat(sanitizer.identifier("ORD-1<script>" + "9".repeat(80)))
                .startsWith("ORD-1script").hasSize(CauseSanitizer.MAX_IDENTIFIER_LENGTH);
        assertThat(sanitizer.cause(null)).isEqualTo("unknown");
        assertThat(sanitizer.identifier(null)).isEmpty();
        assertThat(sanitizer.cause("  ")).isEqualTo("unknown");
    }

    @Test
    void should_build_contract_headers_from_record_failures() {
        final ConsumerRecord<String, byte[]> record = record(3);
        final RecordProcessingFailure failure = RecordProcessingFailure.of(
                ErrorCategory.EXTERNAL_TRANSIENT, "products-api responded 503",
                new MessageIds("ORD-1", "EVT-1"), null, null);

        final Headers built = headers.create(record,
                new ListenerExecutionFailedException("wrapped", failure));

        assertThat(text(built, DltHeaders.ERROR_CATEGORY)).isEqualTo("EXTERNAL_TRANSIENT");
        assertThat(text(built, DltHeaders.ERROR_CAUSE)).isEqualTo("products-api responded 503");
        assertThat(text(built, DltHeaders.ATTEMPTS)).isEqualTo("3");
        assertThat(text(built, DltHeaders.FAILED_AT)).isEqualTo("2026-09-22T10:00:00Z");
        assertThat(text(built, DltHeaders.COMPONENT)).isEqualTo("order-processor");
        assertThat(text(built, DltHeaders.ORDER_ID)).isEqualTo("ORD-1");
        assertThat(text(built, DltHeaders.EVENT_ID)).isEqualTo("EVT-1");
    }

    @Test
    void should_describe_unknown_failures_as_unexpected_without_ids() {
        final Headers built = headers.create(record(0),
                new IllegalStateException("wrapper", new NullPointerException("secret data")));

        assertThat(text(built, DltHeaders.ERROR_CATEGORY)).isEqualTo("UNEXPECTED");
        assertThat(text(built, DltHeaders.ERROR_CAUSE)).isEqualTo("NullPointerException");
        assertThat(text(built, DltHeaders.ATTEMPTS)).isEqualTo("1");
        assertThat(built.lastHeader(DltHeaders.ORDER_ID)).isNull();
    }

    @Test
    void should_skip_identifiers_that_sanitize_to_nothing() {
        final RecordProcessingFailure failure = RecordProcessingFailure.of(
                ErrorCategory.VALIDATION, "bad", new MessageIds("<>", "  "), null, null);

        final Headers built = headers.create(record(1), failure);

        assertThat(built.lastHeader(DltHeaders.ORDER_ID)).isNull();
        assertThat(built.lastHeader(DltHeaders.EVENT_ID)).isNull();
    }

    @Test
    void should_reuse_description_carried_by_the_recoverer() {
        final FailureDescription description = new FailureDescription(
                ErrorCategory.PERSISTENCE, "mongo down", new MessageIds("ORD-9", "EVT-9"), null);

        final Headers built = headers.create(record(2),
                new DescribedFailure(new IllegalStateException(), description));

        assertThat(text(built, DltHeaders.ERROR_CATEGORY)).isEqualTo("PERSISTENCE");
        assertThat(text(built, DltHeaders.ORDER_ID)).isEqualTo("ORD-9");
        assertThat(description.recordsTechnicalFailure()).isFalse();
    }

    @Test
    void should_read_delivery_attempts_defensively() {
        final ConsumerRecord<String, byte[]> malformed = record(0);
        malformed.headers().add(KafkaHeaders.DELIVERY_ATTEMPT, new byte[] {1});

        assertThat(DeliveryAttempts.of(record(4))).isEqualTo(4);
        assertThat(DeliveryAttempts.of(malformed)).isEqualTo(1);
        assertThat(DeliveryAttempts.of(record(0))).isEqualTo(1);
    }

    private static ConsumerRecord<String, byte[]> record(final int attempt) {
        final ConsumerRecord<String, byte[]> record =
                new ConsumerRecord<>("orders.created.v1", 1, 42L, "ORD-1", new byte[] {1});
        if (attempt > 0) {
            record.headers().add(KafkaHeaders.DELIVERY_ATTEMPT,
                    ByteBuffer.allocate(Integer.BYTES).putInt(attempt).array());
        }
        return record;
    }

    private static String text(final Headers headers, final String name) {
        return new String(headers.lastHeader(name).value(), StandardCharsets.UTF_8);
    }
}
