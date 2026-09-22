package com.grupomariposa.orders.infrastructure.kafka.dlt;

import com.grupomariposa.orders.application.command.OrderCommand;
import com.grupomariposa.orders.application.port.in.RecordTechnicalFailureUseCase;
import com.grupomariposa.orders.application.port.out.ProcessingObserver;
import com.grupomariposa.orders.application.port.out.ProcessingStage;
import com.grupomariposa.orders.domain.model.FailureDetails;
import com.grupomariposa.orders.infrastructure.observability.CauseSanitizer;
import com.grupomariposa.orders.infrastructure.observability.LogContext;
import com.grupomariposa.orders.infrastructure.observability.ProcessingMetrics;
import java.util.Objects;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;

public final class DeadLetterRecoverer implements ConsumerRecordRecoverer {

    private static final Logger LOG = LoggerFactory.getLogger(DeadLetterRecoverer.class);
    private static final String CATEGORY = "category";

    private final ConsumerRecordRecoverer deadLetters;
    private final RecordTechnicalFailureUseCase technicalFailures;
    private final ProcessingObserver observer;
    private final ProcessingMetrics metrics;
    private final CauseSanitizer sanitizer;

    public DeadLetterRecoverer(final ConsumerRecordRecoverer deadLetters,
                               final RecordTechnicalFailureUseCase technicalFailures,
                               final ProcessingObserver observer,
                               final ProcessingMetrics metrics, final CauseSanitizer sanitizer) {
        this.deadLetters = Objects.requireNonNull(deadLetters, "deadLetters");
        this.technicalFailures = Objects.requireNonNull(technicalFailures, "technicalFailures");
        this.observer = Objects.requireNonNull(observer, "observer");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        this.sanitizer = Objects.requireNonNull(sanitizer, "sanitizer");
    }

    @Override
    public void accept(final ConsumerRecord<?, ?> record, final Exception exception) {
        final FailureDescription failure = FailureDescription.of(exception);
        try (LogContext ignored = LogContext.bind(failure.ids().orderId(),
                failure.ids().eventId())) {
            deadLetters.accept(record, exception);
            metrics.deadLettered(failure.category());
            observer.stage(ProcessingStage.SENT_TO_DLT, failure.ids().orderId(),
                    failure.ids().eventId());
            LOG.atWarn().addKeyValue(CATEGORY, failure.category())
                    .log("Record sent to dead letter topic: {}", sanitizer.cause(failure.cause()));
            recordTechnicalFailure(failure, DeliveryAttempts.of(record));
        }
    }

    private void recordTechnicalFailure(final FailureDescription failure, final int attempts) {
        if (!failure.category().recordsTechnicalFailure() || failure.orderCommand().isEmpty()) {
            return;
        }
        final OrderCommand command = failure.orderCommand().get();
        try {
            technicalFailures.record(command, new FailureDetails(failure.category().name(),
                    sanitizer.cause(failure.cause()), attempts));
        } catch (RuntimeException unavailable) {
            LOG.warn("Technical failure could not be recorded: {}",
                    sanitizer.describe(unavailable));
        }
    }
}
