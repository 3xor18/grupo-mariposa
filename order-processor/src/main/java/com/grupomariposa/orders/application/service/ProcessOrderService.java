package com.grupomariposa.orders.application.service;

import com.grupomariposa.orders.application.command.OrderCommand;
import com.grupomariposa.orders.application.error.PersistenceException;
import com.grupomariposa.orders.application.outcome.ProcessingOutcome;
import com.grupomariposa.orders.application.port.in.ProcessOrderUseCase;
import com.grupomariposa.orders.application.port.out.IdGenerator;
import com.grupomariposa.orders.application.port.out.InboxEntry;
import com.grupomariposa.orders.application.port.out.InboxOutcome;
import com.grupomariposa.orders.application.port.out.OrderStore;
import com.grupomariposa.orders.application.port.out.ProcessingObserver;
import com.grupomariposa.orders.application.port.out.ProcessingStage;
import com.grupomariposa.orders.application.port.out.SaveResult;
import com.grupomariposa.orders.domain.model.Decision;
import com.grupomariposa.orders.domain.model.EvaluationInput;
import com.grupomariposa.orders.domain.model.Order;
import com.grupomariposa.orders.domain.service.OrderEvaluator;
import java.util.Objects;
import java.util.Optional;

public final class ProcessOrderService implements ProcessOrderUseCase {

    private static final String STATE_CHANGED =
            "Stored order moved to version %d in status %s between read and write";
    private static final String CONCURRENT_CHANGE =
            "Order changed concurrently; retrying the record";

    private final OrderEnricher enricher;
    private final OrderEvaluator evaluator;
    private final OrderAssembler assembler;
    private final OrderStore store;
    private final IdGenerator idGenerator;
    private final ProcessingObserver observer;
    private final VersionArbiter arbiter;

    public ProcessOrderService(final OrderEnricher enricher, final OrderEvaluator evaluator,
                               final OrderAssembler assembler, final OrderStore store,
                               final IdGenerator idGenerator, final ProcessingObserver observer,
                               final VersionArbiter arbiter) {
        this.enricher = Objects.requireNonNull(enricher, "enricher");
        this.evaluator = Objects.requireNonNull(evaluator, "evaluator");
        this.assembler = Objects.requireNonNull(assembler, "assembler");
        this.store = Objects.requireNonNull(store, "store");
        this.idGenerator = Objects.requireNonNull(idGenerator, "idGenerator");
        this.observer = Objects.requireNonNull(observer, "observer");
        this.arbiter = Objects.requireNonNull(arbiter, "arbiter");
    }

    @Override
    public ProcessingOutcome process(final OrderCommand command) {
        return alreadyHandled(command)
                .map(known -> conclude(command, known))
                .orElseGet(() -> conclude(command, evaluateAndPersist(command)));
    }

    private ProcessingOutcome evaluateAndPersist(final OrderCommand command) {
        final EvaluationInput input = enricher.enrich(command);
        observer.stage(ProcessingStage.ENRICHED, command.orderId(), command.eventId());
        final Decision decision = evaluator.evaluate(input);
        observer.stage(ProcessingStage.EVALUATED, command.orderId(), command.eventId());
        return persist(command, assembler.decided(command, input, decision));
    }

    private Optional<ProcessingOutcome> alreadyHandled(final OrderCommand command) {
        if (store.inboxContains(command.eventId())) {
            return Optional.of(duplicate(command));
        }
        return store.findState(command.orderId())
                .flatMap(state -> arbiter.classify(command, state));
    }

    private ProcessingOutcome persist(final OrderCommand command, final Order order) {
        return switch (store.save(order, idGenerator.newEventId())) {
            case SaveResult.Saved saved -> new ProcessingOutcome.Processed(order);
            case SaveResult.DuplicateEvent duplicate -> duplicate(command);
            case SaveResult.Superseded superseded -> arbiter.classify(command, superseded.current())
                    .orElseThrow(() -> new PersistenceException(CONCURRENT_CHANGE,
                            new IllegalStateException(STATE_CHANGED.formatted(
                                    superseded.current().eventVersion(),
                                    superseded.current().status()))));
        };
    }

    private ProcessingOutcome conclude(final OrderCommand command,
                                       final ProcessingOutcome outcome) {
        if (outcome instanceof ProcessingOutcome.Stale) {
            store.recordInbox(new InboxEntry(command.eventId(), command.orderId(),
                    command.eventVersion(), InboxOutcome.STALE,
                    command.reception().receivedAt()));
        }
        observer.outcome(outcome);
        return outcome;
    }

    private static ProcessingOutcome duplicate(final OrderCommand command) {
        return new ProcessingOutcome.Duplicate(command.orderId(), command.eventId());
    }
}
