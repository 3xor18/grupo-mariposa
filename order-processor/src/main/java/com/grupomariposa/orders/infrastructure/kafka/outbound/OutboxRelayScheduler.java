package com.grupomariposa.orders.infrastructure.kafka.outbound;

import com.grupomariposa.orders.application.port.in.PublishPendingEventsUseCase;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

public final class OutboxRelayScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(OutboxRelayScheduler.class);

    private final PublishPendingEventsUseCase relay;

    public OutboxRelayScheduler(final PublishPendingEventsUseCase relay) {
        this.relay = Objects.requireNonNull(relay, "relay");
    }

    @Scheduled(fixedDelayString = "${app.outbox.fixed-delay}",
            initialDelayString = "${app.outbox.fixed-delay}")
    public void relay() {
        try {
            relay.publishPending();
        } catch (RuntimeException failure) {
            LOG.warn("Outbox relay cycle failed: {}", failure.getClass().getSimpleName());
        }
    }
}
