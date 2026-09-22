package com.grupomariposa.orders.infrastructure.kafka.dlt;

import com.grupomariposa.orders.application.command.OrderCommand;
import com.grupomariposa.orders.application.error.ErrorCategory;
import com.grupomariposa.orders.infrastructure.kafka.inbound.MessageIds;
import com.grupomariposa.orders.infrastructure.kafka.inbound.RecordProcessingFailure;
import java.util.Optional;

public record FailureDescription(ErrorCategory category, String cause, MessageIds ids,
                                 OrderCommand command) {

    public static FailureDescription of(final Throwable failure) {
        return RecordProcessingFailure.find(failure)
                .map(known -> new FailureDescription(known.category(), known.getMessage(),
                        known.ids(), known.command().orElse(null)))
                .orElseGet(() -> new FailureDescription(ErrorCategory.UNEXPECTED,
                        rootCause(failure).getClass().getSimpleName(), MessageIds.UNKNOWN,
                        null));
    }

    public Optional<OrderCommand> orderCommand() {
        return Optional.ofNullable(command);
    }

    private static Throwable rootCause(final Throwable failure) {
        Throwable root = failure;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root;
    }
}
