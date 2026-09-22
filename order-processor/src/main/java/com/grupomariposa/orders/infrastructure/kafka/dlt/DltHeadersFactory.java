package com.grupomariposa.orders.infrastructure.kafka.dlt;

import com.grupomariposa.orders.infrastructure.observability.CauseSanitizer;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Objects;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;

public final class DltHeadersFactory {

    private final Clock clock;
    private final CauseSanitizer sanitizer;

    public DltHeadersFactory(final Clock clock, final CauseSanitizer sanitizer) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.sanitizer = Objects.requireNonNull(sanitizer, "sanitizer");
    }

    public Headers create(final ConsumerRecord<?, ?> record, final Exception exception) {
        final FailureDescription failure = FailureDescription.of(exception);
        final Headers headers = new RecordHeaders();
        add(headers, DltHeaders.ERROR_CATEGORY, failure.category().name());
        add(headers, DltHeaders.ERROR_CAUSE, sanitizer.cause(failure.cause()));
        add(headers, DltHeaders.ATTEMPTS, String.valueOf(DeliveryAttempts.of(record)));
        add(headers, DltHeaders.FAILED_AT, clock.instant().toString());
        add(headers, DltHeaders.COMPONENT, DltHeaders.COMPONENT_NAME);
        failure.ids().order().map(sanitizer::identifier).filter(id -> !id.isEmpty())
                .ifPresent(orderId -> add(headers, DltHeaders.ORDER_ID, orderId));
        failure.ids().event().map(sanitizer::identifier).filter(id -> !id.isEmpty())
                .ifPresent(eventId -> add(headers, DltHeaders.EVENT_ID, eventId));
        return headers;
    }

    private static void add(final Headers headers, final String name, final String value) {
        headers.add(name, value.getBytes(StandardCharsets.UTF_8));
    }
}
