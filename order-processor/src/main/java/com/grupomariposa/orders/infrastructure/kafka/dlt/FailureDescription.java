package com.grupomariposa.orders.infrastructure.kafka.dlt;

import com.grupomariposa.orders.application.command.OrderCommand;
import com.grupomariposa.orders.application.error.ErrorCategory;
import com.grupomariposa.orders.infrastructure.kafka.inbound.MessageIds;
import com.grupomariposa.orders.infrastructure.kafka.inbound.RecordProcessingFailure;
import java.util.Objects;

public record FailureDescription(ErrorCategory category, String cause, MessageIds ids,
                                 OrderCommand command) {

    public FailureDescription {
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(ids, "ids");
    }

    public static FailureDescription of(final Throwable failure) {
        if (failure instanceof DescribedFailure described) {
            return described.description();
        }
        return RecordProcessingFailure.find(failure)
                .map(known -> new FailureDescription(known.category(), known.getMessage(),
                        known.ids(), known.command().orElse(null)))
                .orElseGet(() -> new FailureDescription(ErrorCategory.UNEXPECTED,
                        rootCause(failure).getClass().getSimpleName(), MessageIds.UNKNOWN,
                        null));
    }

    public boolean recordsTechnicalFailure() {
        return command != null && category.recordsTechnicalFailure();
    }

    private static Throwable rootCause(final Throwable failure) {
        Throwable root = failure;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root;
    }
}
