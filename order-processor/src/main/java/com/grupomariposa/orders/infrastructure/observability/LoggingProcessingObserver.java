package com.grupomariposa.orders.infrastructure.observability;

import com.grupomariposa.orders.application.outcome.ProcessingOutcome;
import com.grupomariposa.orders.application.port.out.PendingEvent;
import com.grupomariposa.orders.application.port.out.ProcessingObserver;
import com.grupomariposa.orders.application.port.out.ProcessingStage;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class LoggingProcessingObserver implements ProcessingObserver {

    public static final String PROCESSED = "orders.processed";
    public static final String REJECTED = "orders.rejected";
    public static final String DUPLICATES = "orders.duplicates";
    public static final String STALE = "orders.stale";
    public static final String CONFLICTS = "orders.conflicts";
    public static final String TECHNICAL_FAILURES = "orders.technical_failures";
    public static final String OUTBOX_PUBLISHED = "orders.outbox.published";
    public static final String OUTBOX_FAILURES = "orders.outbox.failures";
    private static final String STATUS = "status";
    private static final String REASON = "reason";
    private static final String CATEGORY = "category";
    private static final String STAGE_KEY = "stage";
    private static final String STAGE_MESSAGE = "Order {}";
    private static final Logger LOG = LoggerFactory.getLogger(LoggingProcessingObserver.class);

    private final MeterRegistry registry;

    public LoggingProcessingObserver(final MeterRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    @Override
    public void stage(final ProcessingStage stage, final String orderId, final String eventId) {
        try (LogContext ignored = LogContext.bind(orderId, eventId)) {
            LOG.atInfo().addKeyValue(STAGE_KEY, stage).log(STAGE_MESSAGE, stage);
        }
    }

    @Override
    public void outcome(final ProcessingOutcome outcome) {
        final ProcessingStage stage = switch (outcome) {
            case ProcessingOutcome.Processed processed -> processed(processed);
            case ProcessingOutcome.Duplicate ignored ->
                    count(DUPLICATES, ProcessingStage.DUPLICATE);
            case ProcessingOutcome.Stale ignored -> count(STALE, ProcessingStage.STALE);
            case ProcessingOutcome.VersionConflict ignored ->
                    count(CONFLICTS, ProcessingStage.CONFLICT);
            case ProcessingOutcome.TechnicalFailure failure -> technicalFailure(failure);
        };
        stage(stage, outcome.orderId(), outcome.eventId());
    }

    @Override
    public void published(final PendingEvent event) {
        registry.counter(OUTBOX_PUBLISHED).increment();
        stage(ProcessingStage.PUBLISHED, event.orderId(), event.eventId());
    }

    @Override
    public void publicationFailed(final PendingEvent event, final Throwable cause) {
        registry.counter(OUTBOX_FAILURES).increment();
        try (LogContext ignored = LogContext.bind(event.orderId(), event.eventId())) {
            LOG.atWarn().addKeyValue(STAGE_KEY, ProcessingStage.PUBLICATION_FAILED)
                    .log("Outbox publication failed: {}", cause.getClass().getSimpleName());
        }
    }

    private ProcessingStage processed(final ProcessingOutcome.Processed processed) {
        registry.counter(PROCESSED, STATUS, processed.order().status().name()).increment();
        processed.order().reason().ifPresent(reason ->
                registry.counter(REJECTED, REASON, reason.name()).increment());
        return ProcessingStage.PERSISTED;
    }

    private ProcessingStage technicalFailure(final ProcessingOutcome.TechnicalFailure failure) {
        registry.counter(TECHNICAL_FAILURES, CATEGORY, failure.category()).increment();
        return ProcessingStage.TECHNICAL_FAILURE;
    }

    private ProcessingStage count(final String metric, final ProcessingStage stage) {
        registry.counter(metric).increment();
        return stage;
    }
}
