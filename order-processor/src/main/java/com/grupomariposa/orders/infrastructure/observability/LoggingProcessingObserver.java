package com.grupomariposa.orders.infrastructure.observability;

import com.grupomariposa.orders.application.outcome.ProcessingOutcome;
import com.grupomariposa.orders.application.port.out.PendingEvent;
import com.grupomariposa.orders.application.port.out.ProcessingObserver;
import com.grupomariposa.orders.application.port.out.ProcessingStage;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

public final class LoggingProcessingObserver implements ProcessingObserver {

    private static final String STAGE_KEY = "stage";
    private static final String STAGE_MESSAGE = "Order {}";
    private static final String PUBLICATION_FAILED = "Outbox publication failed: {}";
    private static final String LEASE_LOST = "Outbox lease was taken over by another relay";
    private static final Logger LOG = LoggerFactory.getLogger(LoggingProcessingObserver.class);

    private final CauseSanitizer sanitizer;

    public LoggingProcessingObserver(final CauseSanitizer sanitizer) {
        this.sanitizer = Objects.requireNonNull(sanitizer, "sanitizer");
    }

    @Override
    public void stage(final ProcessingStage stage, final String orderId, final String eventId) {
        final Level level = stage.isTerminal() ? Level.INFO : Level.DEBUG;
        if (!LOG.isEnabledForLevel(level)) {
            return;
        }
        try (LogContext ignored = LogContext.bind(orderId, eventId)) {
            LOG.atLevel(level).addKeyValue(STAGE_KEY, stage).log(STAGE_MESSAGE, stage);
        }
    }

    @Override
    public void outcome(final ProcessingOutcome outcome) {
        stage(stageOf(outcome), outcome.orderId(), outcome.eventId());
    }

    @Override
    public void published(final PendingEvent event) {
        stage(ProcessingStage.PUBLISHED, event.orderId(), event.eventId());
    }

    @Override
    public void publicationFailed(final PendingEvent event, final Throwable cause) {
        try (LogContext ignored = LogContext.bind(event.orderId(), event.eventId())) {
            LOG.atWarn().addKeyValue(STAGE_KEY, ProcessingStage.PUBLICATION_FAILED)
                    .log(PUBLICATION_FAILED, sanitizer.describe(cause));
        }
    }

    @Override
    public void leaseLost(final PendingEvent event) {
        try (LogContext ignored = LogContext.bind(event.orderId(), event.eventId())) {
            LOG.atWarn().addKeyValue(STAGE_KEY, ProcessingStage.PUBLICATION_FAILED)
                    .log(LEASE_LOST);
        }
    }

    private static ProcessingStage stageOf(final ProcessingOutcome outcome) {
        return switch (outcome) {
            case ProcessingOutcome.Processed ignored -> ProcessingStage.PERSISTED;
            case ProcessingOutcome.Duplicate ignored -> ProcessingStage.DUPLICATE;
            case ProcessingOutcome.Stale ignored -> ProcessingStage.STALE;
            case ProcessingOutcome.VersionConflict ignored -> ProcessingStage.CONFLICT;
            case ProcessingOutcome.TechnicalFailure ignored -> ProcessingStage.TECHNICAL_FAILURE;
        };
    }
}
