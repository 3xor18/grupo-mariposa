package com.grupomariposa.orders.infrastructure.kafka.inbound;

import com.grupomariposa.orders.application.command.OrderCommand;
import com.grupomariposa.orders.application.error.ErrorCategory;
import java.util.Objects;
import java.util.Optional;

public class RecordProcessingFailure extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final ErrorCategory category;
    private final transient MessageIds ids;
    private final transient OrderCommand command;

    protected RecordProcessingFailure(final ErrorCategory category, final String cause,
                                      final MessageIds ids, final OrderCommand command,
                                      final Throwable origin) {
        super(cause, origin);
        this.category = Objects.requireNonNull(category, "category");
        this.ids = Objects.requireNonNull(ids, "ids");
        this.command = command;
    }

    public static RecordProcessingFailure of(final ErrorCategory category, final String cause,
                                             final MessageIds ids, final OrderCommand command,
                                             final Throwable origin) {
        return category.isRetryable()
                ? new RetryableRecordFailure(category, cause, ids, command, origin)
                : new RecordProcessingFailure(category, cause, ids, command, origin);
    }

    public static Optional<RecordProcessingFailure> find(final Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof RecordProcessingFailure recordFailure) {
                return Optional.of(recordFailure);
            }
        }
        return Optional.empty();
    }

    public ErrorCategory category() {
        return category;
    }

    public MessageIds ids() {
        return ids;
    }

    public Optional<OrderCommand> command() {
        return Optional.ofNullable(command);
    }
}
