package com.grupomariposa.orders.infrastructure.kafka.inbound;

import com.grupomariposa.orders.application.command.OrderCommand;
import com.grupomariposa.orders.application.error.ErrorCategory;

public final class RetryableRecordFailure extends RecordProcessingFailure {

    private static final long serialVersionUID = 1L;

    RetryableRecordFailure(final ErrorCategory category, final String cause, final MessageIds ids,
                           final OrderCommand command, final Throwable origin) {
        super(category, cause, ids, command, origin);
    }
}
