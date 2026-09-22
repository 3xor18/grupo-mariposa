package com.grupomariposa.orders.infrastructure.kafka.inbound;

import com.grupomariposa.orders.application.command.OrderCommand;
import com.grupomariposa.orders.application.command.Reception;
import com.grupomariposa.orders.application.error.ErrorCategory;
import com.grupomariposa.orders.application.error.ProcessingException;
import com.grupomariposa.orders.application.outcome.ProcessingOutcome;
import com.grupomariposa.orders.application.port.in.ProcessOrderUseCase;
import com.grupomariposa.orders.application.port.out.ProcessingObserver;
import com.grupomariposa.orders.application.port.out.ProcessingStage;
import com.grupomariposa.orders.application.port.out.TimeProvider;
import com.grupomariposa.orders.application.validation.OrderCommandValidator;
import com.grupomariposa.orders.application.validation.ValidationResult;
import com.grupomariposa.orders.infrastructure.observability.LogContext;
import com.grupomariposa.orders.infrastructure.observability.ProcessingMetrics;
import com.grupomariposa.orders.infrastructure.observability.TraceContext;
import io.micrometer.core.instrument.Timer;
import java.util.Objects;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;

public final class OrderCreatedListener {

    public static final String LISTENER_ID = "orders-created-listener";
    private static final String CONFLICT =
            "Version %d of the order was already decided by event %s";

    private final OrderMessageReader reader;
    private final OrderMessageMapper mapper;
    private final OrderCommandValidator validator;
    private final ProcessOrderUseCase useCase;
    private final ProcessingObserver observer;
    private final TimeProvider timeProvider;
    private final TraceContext traceContext;
    private final ProcessingMetrics metrics;

    public OrderCreatedListener(final OrderMessageReader reader,
                                final OrderMessageMapper mapper,
                                final OrderCommandValidator validator,
                                final ProcessOrderUseCase useCase,
                                final ProcessingObserver observer,
                                final TimeProvider timeProvider,
                                final TraceContext traceContext,
                                final ProcessingMetrics metrics) {
        this.reader = Objects.requireNonNull(reader, "reader");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.validator = Objects.requireNonNull(validator, "validator");
        this.useCase = Objects.requireNonNull(useCase, "useCase");
        this.observer = Objects.requireNonNull(observer, "observer");
        this.timeProvider = Objects.requireNonNull(timeProvider, "timeProvider");
        this.traceContext = Objects.requireNonNull(traceContext, "traceContext");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
    }

    @KafkaListener(id = LISTENER_ID, topics = "${app.kafka.topics.orders-created}",
            groupId = "${app.kafka.consumer-group}", concurrency = "${app.kafka.concurrency}")
    public void onMessage(final ConsumerRecord<String, byte[]> record) {
        final Timer.Sample sample = metrics.start();
        try {
            handle(record);
        } finally {
            metrics.stop(sample);
        }
    }

    private void handle(final ConsumerRecord<String, byte[]> record) {
        final Reception reception = new Reception(timeProvider.now(),
                traceContext.currentTraceId().orElse(null));
        final OrderCreatedMessage message = reader.read(record.value());
        final MessageIds ids = new MessageIds(message.orderId(), message.eventId());
        try (LogContext ignored = LogContext.bind(message.orderId(), message.eventId())) {
            observer.stage(ProcessingStage.RECEIVED, message.orderId(), message.eventId());
            final OrderCommand command = validated(message, ids, reception);
            observer.stage(ProcessingStage.VALIDATED, command.orderId(), command.eventId());
            rejectConflicts(command, execute(command, ids));
        }
    }

    private OrderCommand validated(final OrderCreatedMessage message, final MessageIds ids,
                                   final Reception reception) {
        return switch (validator.validate(mapper.toUnvalidated(message), reception)) {
            case ValidationResult.Valid valid -> valid.command();
            case ValidationResult.Invalid invalid -> throw RecordProcessingFailure.of(
                    ErrorCategory.VALIDATION, invalid.summary(), ids, null, null);
        };
    }

    private ProcessingOutcome execute(final OrderCommand command, final MessageIds ids) {
        try {
            return useCase.process(command);
        } catch (ProcessingException failure) {
            throw RecordProcessingFailure.of(failure.category(), failure.getMessage(), ids,
                    command, failure);
        } catch (RuntimeException unexpected) {
            throw RecordProcessingFailure.of(ErrorCategory.UNEXPECTED,
                    unexpected.getClass().getSimpleName(), ids, command, unexpected);
        }
    }

    private static void rejectConflicts(final OrderCommand command,
                                        final ProcessingOutcome outcome) {
        if (outcome instanceof ProcessingOutcome.VersionConflict conflict) {
            throw RecordProcessingFailure.of(ErrorCategory.VERSION_CONFLICT,
                    CONFLICT.formatted(conflict.version(), conflict.winningEventId()),
                    new MessageIds(command.orderId(), command.eventId()), null, null);
        }
    }
}
