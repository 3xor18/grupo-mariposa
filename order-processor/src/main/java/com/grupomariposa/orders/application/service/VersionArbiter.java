package com.grupomariposa.orders.application.service;

import com.grupomariposa.orders.application.command.OrderCommand;
import com.grupomariposa.orders.application.outcome.ProcessingOutcome;
import com.grupomariposa.orders.application.port.out.StoredOrderState;
import java.util.Optional;

public final class VersionArbiter {

    public Optional<ProcessingOutcome> classify(final OrderCommand command,
                                                final StoredOrderState stored) {
        if (stored.eventVersion() > command.eventVersion()) {
            return Optional.of(new ProcessingOutcome.Stale(command.orderId(), command.eventId(),
                    command.eventVersion(), stored.eventVersion()));
        }
        if (stored.eventVersion() < command.eventVersion()) {
            return Optional.empty();
        }
        if (!stored.sourceEventId().equals(command.eventId())) {
            return Optional.of(new ProcessingOutcome.VersionConflict(command.orderId(),
                    command.eventId(), command.eventVersion(), stored.sourceEventId()));
        }
        return stored.status().isTerminal()
                ? Optional.of(new ProcessingOutcome.Duplicate(command.orderId(), command.eventId()))
                : Optional.empty();
    }
}
