package com.grupomariposa.orders.infrastructure.observability;

import com.grupomariposa.orders.application.outcome.ProcessingOutcome;
import com.grupomariposa.orders.application.port.out.PendingEvent;
import com.grupomariposa.orders.application.port.out.ProcessingObserver;
import com.grupomariposa.orders.application.port.out.ProcessingStage;
import java.util.List;

public final class CompositeProcessingObserver implements ProcessingObserver {

    private final List<ProcessingObserver> observers;

    public CompositeProcessingObserver(final List<ProcessingObserver> observers) {
        this.observers = List.copyOf(observers);
    }

    @Override
    public void stage(final ProcessingStage stage, final String orderId, final String eventId) {
        observers.forEach(observer -> observer.stage(stage, orderId, eventId));
    }

    @Override
    public void outcome(final ProcessingOutcome outcome) {
        observers.forEach(observer -> observer.outcome(outcome));
    }

    @Override
    public void published(final PendingEvent event) {
        observers.forEach(observer -> observer.published(event));
    }

    @Override
    public void publicationFailed(final PendingEvent event, final Throwable cause) {
        observers.forEach(observer -> observer.publicationFailed(event, cause));
    }

    @Override
    public void leaseLost(final PendingEvent event) {
        observers.forEach(observer -> observer.leaseLost(event));
    }
}
