package com.grupomariposa.orders.application.service;

import com.grupomariposa.orders.application.command.OrderCommand;
import com.grupomariposa.orders.application.outcome.ProcessingOutcome;
import com.grupomariposa.orders.application.port.in.RecordTechnicalFailureUseCase;
import com.grupomariposa.orders.application.port.out.OrderStore;
import com.grupomariposa.orders.application.port.out.ProcessingObserver;
import com.grupomariposa.orders.domain.model.FailureDetails;
import java.util.Objects;

public final class RecordTechnicalFailureService implements RecordTechnicalFailureUseCase {

    private final OrderAssembler assembler;
    private final OrderStore store;
    private final ProcessingObserver observer;

    public RecordTechnicalFailureService(final OrderAssembler assembler, final OrderStore store,
                                         final ProcessingObserver observer) {
        this.assembler = Objects.requireNonNull(assembler, "assembler");
        this.store = Objects.requireNonNull(store, "store");
        this.observer = Objects.requireNonNull(observer, "observer");
    }

    @Override
    public ProcessingOutcome.TechnicalFailure record(final OrderCommand command,
                                                     final FailureDetails failure) {
        final boolean recorded = store.saveTechnicalFailure(
                assembler.technicalFailure(command, failure));
        final ProcessingOutcome.TechnicalFailure outcome = new ProcessingOutcome.TechnicalFailure(
                command.orderId(), command.eventId(), failure.category(), recorded);
        observer.outcome(outcome);
        return outcome;
    }
}
