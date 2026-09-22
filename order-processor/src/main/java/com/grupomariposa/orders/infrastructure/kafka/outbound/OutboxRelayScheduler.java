package com.grupomariposa.orders.infrastructure.kafka.outbound;

import com.grupomariposa.orders.application.port.in.PublishPendingEventsUseCase;
import com.grupomariposa.orders.infrastructure.observability.CauseSanitizer;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

public final class OutboxRelayScheduler {

    public static final String RELAY_FAILURES = "orders.outbox.relay.failures";
    private static final Logger LOG = LoggerFactory.getLogger(OutboxRelayScheduler.class);

    private final PublishPendingEventsUseCase relay;
    private final CauseSanitizer sanitizer;
    private final Counter failures;

    public OutboxRelayScheduler(final PublishPendingEventsUseCase relay,
                                final CauseSanitizer sanitizer, final MeterRegistry registry) {
        this.relay = Objects.requireNonNull(relay, "relay");
        this.sanitizer = Objects.requireNonNull(sanitizer, "sanitizer");
        this.failures = Counter.builder(RELAY_FAILURES)
                .description("Outbox relay cycles aborted by an unexpected failure")
                .register(registry);
    }

    @Scheduled(fixedDelayString = "${app.outbox.fixed-delay}",
            initialDelayString = "${app.outbox.fixed-delay}")
    public void relay() {
        try {
            relay.publishPending();
        } catch (RuntimeException failure) {
            failures.increment();
            LOG.warn("Outbox relay cycle failed: {}", sanitizer.describe(failure));
        }
    }
}
