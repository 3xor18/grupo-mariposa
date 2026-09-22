package com.grupomariposa.orders.application.service;

import com.grupomariposa.orders.application.port.in.PublishPendingEventsUseCase;
import com.grupomariposa.orders.application.port.out.EventPublisher;
import com.grupomariposa.orders.application.port.out.OutboxStore;
import com.grupomariposa.orders.application.port.out.PendingEvent;
import com.grupomariposa.orders.application.port.out.ProcessingObserver;
import com.grupomariposa.orders.application.port.out.TimeProvider;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class PublishPendingEventsService implements PublishPendingEventsUseCase {

    private final OutboxStore outbox;
    private final EventPublisher publisher;
    private final TimeProvider timeProvider;
    private final ProcessingObserver observer;
    private final RelaySettings settings;

    public PublishPendingEventsService(final OutboxStore outbox, final EventPublisher publisher,
                                       final TimeProvider timeProvider,
                                       final ProcessingObserver observer,
                                       final RelaySettings settings) {
        this.outbox = Objects.requireNonNull(outbox, "outbox");
        this.publisher = Objects.requireNonNull(publisher, "publisher");
        this.timeProvider = Objects.requireNonNull(timeProvider, "timeProvider");
        this.observer = Objects.requireNonNull(observer, "observer");
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    @Override
    public int publishPending() {
        final Instant now = timeProvider.now();
        final List<PendingEvent> batch = outbox.claim(settings.batchSize(), now,
                now.plus(settings.lease()), settings.owner());
        final List<Dispatch> dispatches = batch.stream()
                .map(event -> new Dispatch(event, send(event)))
                .toList();
        return (int) dispatches.stream().filter(this::settle).count();
    }

    private CompletableFuture<Void> send(final PendingEvent event) {
        try {
            return publisher.publish(event);
        } catch (RuntimeException failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }

    private boolean settle(final Dispatch dispatch) {
        try {
            dispatch.result().get(settings.sendTimeout().toMillis(), TimeUnit.MILLISECONDS);
            outbox.markPublished(dispatch.event().eventId(), timeProvider.now());
            observer.published(dispatch.event());
            return true;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return release(dispatch.event(), interrupted);
        } catch (ExecutionException | TimeoutException failure) {
            return release(dispatch.event(), failure);
        }
    }

    private boolean release(final PendingEvent event, final Throwable cause) {
        final int attempts = event.attempts() + 1;
        outbox.release(event.eventId(), attempts,
                timeProvider.now().plus(settings.backoffFor(attempts)));
        observer.publicationFailed(event, cause);
        return false;
    }

    private record Dispatch(PendingEvent event, CompletableFuture<Void> result) {
    }
}
