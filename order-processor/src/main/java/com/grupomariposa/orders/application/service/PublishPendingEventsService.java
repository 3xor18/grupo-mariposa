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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class PublishPendingEventsService implements PublishPendingEventsUseCase {

    private static final String SEND_TIMED_OUT =
            "Broker acknowledgement exceeded the batch deadline";

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
        awaitWithinDeadline(dispatches);
        return (int) dispatches.stream().filter(this::settle).count();
    }

    private CompletableFuture<Void> send(final PendingEvent event) {
        try {
            return publisher.publish(event);
        } catch (RuntimeException failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }

    private void awaitWithinDeadline(final List<Dispatch> dispatches) {
        final CompletableFuture<?>[] results = dispatches.stream()
                .map(Dispatch::result)
                .toArray(CompletableFuture<?>[]::new);
        CompletableFuture.allOf(results)
                .handle((ignored, failure) -> Boolean.TRUE)
                .completeOnTimeout(Boolean.FALSE, settings.sendTimeout().toMillis(),
                        TimeUnit.MILLISECONDS)
                .join();
    }

    private boolean settle(final Dispatch dispatch) {
        final CompletableFuture<Void> result = dispatch.result();
        if (result.isDone() && !result.isCompletedExceptionally()) {
            return confirm(dispatch.event());
        }
        result.cancel(false);
        release(dispatch.event(), result.isCompletedExceptionally() && !result.isCancelled()
                ? result.exceptionNow() : new TimeoutException(SEND_TIMED_OUT));
        return false;
    }

    private boolean confirm(final PendingEvent event) {
        try {
            if (outbox.markPublished(event.eventId(), settings.owner(), timeProvider.now())) {
                observer.published(event);
                return true;
            }
            observer.leaseLost(event);
        } catch (RuntimeException storeFailure) {
            observer.publicationFailed(event, storeFailure);
        }
        return false;
    }

    private void release(final PendingEvent event, final Throwable cause) {
        observer.publicationFailed(event, cause);
        final int attempts = event.attempts() + 1;
        try {
            if (!outbox.release(event.eventId(), settings.owner(), attempts,
                    timeProvider.now().plus(settings.backoffFor(attempts)))) {
                observer.leaseLost(event);
            }
        } catch (RuntimeException storeFailure) {
            observer.publicationFailed(event, storeFailure);
        }
    }

    private record Dispatch(PendingEvent event, CompletableFuture<Void> result) {
    }
}
