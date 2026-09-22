package com.grupomariposa.orders.infrastructure.observability;

import com.grupomariposa.orders.application.outcome.ProcessingOutcome;
import com.grupomariposa.orders.application.port.out.PendingEvent;
import com.grupomariposa.orders.application.port.out.ProcessingObserver;
import com.grupomariposa.orders.application.port.out.ProcessingStage;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Objects;

public final class MetricsProcessingObserver implements ProcessingObserver {

    public static final String PROCESSED = "orders.processed";
    public static final String REJECTED = "orders.rejected";
    public static final String DUPLICATES = "orders.duplicates";
    public static final String STALE = "orders.stale";
    public static final String CONFLICTS = "orders.conflicts";
    public static final String TECHNICAL_FAILURES = "orders.technical_failures";
    public static final String OUTBOX_PUBLISHED = "orders.outbox.published";
    public static final String OUTBOX_FAILURES = "orders.outbox.failures";
    public static final String OUTBOX_LEASE_LOST = "orders.outbox.lease_lost";
    private static final String STATUS = "status";
    private static final String REASON = "reason";
    private static final String CATEGORY = "category";

    private final MeterRegistry registry;

    public MetricsProcessingObserver(final MeterRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    @Override
    public void stage(final ProcessingStage stage, final String orderId, final String eventId) {
        registry.counter(ProcessingMetrics.STAGES, ProcessingMetrics.STAGE, stage.name())
                .increment();
    }

    @Override
    public void outcome(final ProcessingOutcome outcome) {
        switch (outcome) {
            case ProcessingOutcome.Processed processed -> processed(processed);
            case ProcessingOutcome.Duplicate ignored -> registry.counter(DUPLICATES).increment();
            case ProcessingOutcome.Stale ignored -> registry.counter(STALE).increment();
            case ProcessingOutcome.VersionConflict ignored ->
                    registry.counter(CONFLICTS).increment();
            case ProcessingOutcome.TechnicalFailure failure ->
                    registry.counter(TECHNICAL_FAILURES, CATEGORY, failure.category()).increment();
        }
    }

    @Override
    public void published(final PendingEvent event) {
        registry.counter(OUTBOX_PUBLISHED).increment();
    }

    @Override
    public void publicationFailed(final PendingEvent event, final Throwable cause) {
        registry.counter(OUTBOX_FAILURES).increment();
    }

    @Override
    public void leaseLost(final PendingEvent event) {
        registry.counter(OUTBOX_LEASE_LOST).increment();
    }

    private void processed(final ProcessingOutcome.Processed processed) {
        registry.counter(PROCESSED, STATUS, processed.order().status().name()).increment();
        processed.order().reason().ifPresent(reason ->
                registry.counter(REJECTED, REASON, reason.name()).increment());
    }
}
