package com.grupomariposa.orders.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.grupomariposa.orders.application.port.out.EventPublisher;
import com.grupomariposa.orders.application.port.out.OutboxStore;
import com.grupomariposa.orders.application.port.out.PendingEvent;
import com.grupomariposa.orders.application.port.out.ProcessingObserver;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PublishPendingEventsServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-18T15:00:00Z");
    private static final RelaySettings SETTINGS = new RelaySettings(10, Duration.ofSeconds(30),
            Duration.ofMillis(50), Duration.ofSeconds(1), Duration.ofSeconds(60), "node-1");
    private static final PendingEvent EVENT =
            new PendingEvent("OUT-1", "ORD-1", "orders.processed.v1", "ORD-1", "{}", 2);

    private final OutboxStore outbox = mock(OutboxStore.class);
    private final EventPublisher publisher = mock(EventPublisher.class);
    private final ProcessingObserver observer = mock(ProcessingObserver.class);
    private final PublishPendingEventsService service =
            new PublishPendingEventsService(outbox, publisher, () -> NOW, observer, SETTINGS);

    @BeforeEach
    void claimOne() {
        when(outbox.claim(10, NOW, NOW.plusSeconds(30), "node-1")).thenReturn(List.of(EVENT));
    }

    @Test
    void should_mark_published_after_broker_ack() {
        when(publisher.publish(EVENT)).thenReturn(CompletableFuture.completedFuture(null));

        assertThat(service.publishPending()).isOne();
        verify(outbox).markPublished("OUT-1", NOW);
        verify(observer).published(EVENT);
    }

    @Test
    void should_release_with_backoff_when_send_fails() {
        final RuntimeException failure = new IllegalStateException("broker down");
        when(publisher.publish(EVENT)).thenReturn(CompletableFuture.failedFuture(failure));

        assertThat(service.publishPending()).isZero();
        verify(outbox).release("OUT-1", 3, NOW.plusSeconds(4));
        verify(outbox, never()).markPublished(any(), any());
        verify(observer).publicationFailed(eq(EVENT), any());
    }

    @Test
    void should_release_when_publisher_throws_synchronously() {
        when(publisher.publish(EVENT)).thenThrow(new IllegalStateException("serializer"));

        assertThat(service.publishPending()).isZero();
        verify(outbox).release("OUT-1", 3, NOW.plusSeconds(4));
    }

    @Test
    void should_release_when_ack_times_out() {
        when(publisher.publish(EVENT)).thenReturn(new CompletableFuture<>());

        assertThat(service.publishPending()).isZero();
        verify(observer).publicationFailed(eq(EVENT), any(TimeoutException.class));
    }

    @Test
    void should_release_and_keep_interrupt_flag_when_interrupted() {
        when(publisher.publish(EVENT)).thenReturn(new CompletableFuture<>());
        Thread.currentThread().interrupt();

        assertThat(service.publishPending()).isZero();
        assertThat(Thread.interrupted()).isTrue();
        verify(outbox).release("OUT-1", 3, NOW.plusSeconds(4));
    }
}
