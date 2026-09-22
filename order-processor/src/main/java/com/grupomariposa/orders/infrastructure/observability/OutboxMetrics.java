package com.grupomariposa.orders.infrastructure.observability;

import com.grupomariposa.orders.infrastructure.persistence.MongoOutboxStore;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.function.ToDoubleFunction;
import org.springframework.dao.DataAccessException;

public final class OutboxMetrics implements MeterBinder {

    public static final String PENDING = "outbox.pending";
    public static final String OLDEST_AGE = "outbox.oldest.age";
    private static final String SECONDS = "seconds";

    private final MongoOutboxStore outbox;
    private final Clock clock;

    public OutboxMetrics(final MongoOutboxStore outbox, final Clock clock) {
        this.outbox = Objects.requireNonNull(outbox, "outbox");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public void bindTo(final MeterRegistry registry) {
        Gauge.builder(PENDING, outbox, safely(store -> store.countUnpublished()))
                .description("Outbox events not yet published")
                .register(registry);
        Gauge.builder(OLDEST_AGE, outbox, safely(this::oldestAgeSeconds))
                .description("Age of the oldest unpublished outbox event")
                .baseUnit(SECONDS)
                .register(registry);
    }

    double oldestAgeSeconds(final MongoOutboxStore store) {
        return store.oldestUnpublishedCreatedAt()
                .map(createdAt -> Duration.between(createdAt, clock.instant()).toMillis())
                .map(millis -> millis / (double) Duration.ofSeconds(1).toMillis())
                .orElse(0.0);
    }

    static ToDoubleFunction<MongoOutboxStore> safely(
            final ToDoubleFunction<MongoOutboxStore> read) {
        return store -> {
            try {
                return read.applyAsDouble(store);
            } catch (DataAccessException unavailable) {
                return Double.NaN;
            }
        };
    }
}
