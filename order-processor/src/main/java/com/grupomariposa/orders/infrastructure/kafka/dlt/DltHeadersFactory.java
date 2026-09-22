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
    private final String componentName;

    public DltHeadersFactory(final Clock clock, final CauseSanitizer sanitizer,
                             final String componentName) {
        this.componentName = Objects.requireNonNull(componentName, "componentName");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.sanitizer = Objects.requireNonNull(sanitizer, "sanitizer");
    }

    public Headers create(final ConsumerRecord<?, ?> consumerRecord, final Exception exception) {
        return create(consumerRecord, FailureDescription.of(exception));
    }

    public Headers create(final ConsumerRecord<?, ?> consumerRecord,
                          final FailureDescription failure) {
        final Headers headers = new RecordHeaders();
        add(headers, DltHeaders.ERROR_CATEGORY, failure.category().name());
        add(headers, DltHeaders.ERROR_CAUSE, sanitizer.cause(failure.cause()));
        add(headers, DltHeaders.ATTEMPTS, String.valueOf(DeliveryAttempts.of(consumerRecord)));
        add(headers, DltHeaders.FAILED_AT, clock.instant().toString());
        add(headers, DltHeaders.COMPONENT, componentName);
        addIdentifier(headers, DltHeaders.ORDER_ID, failure.ids().orderId());
        addIdentifier(headers, DltHeaders.EVENT_ID, failure.ids().eventId());
        return headers;
    }

    private void addIdentifier(final Headers headers, final String name, final String raw) {
        final String identifier = sanitizer.identifier(raw);
        if (!identifier.isEmpty()) {
            add(headers, name, identifier);
        }
    }

    private static void add(final Headers headers, final String name, final String value) {
        headers.add(name, value.getBytes(StandardCharsets.UTF_8));
    }
}
