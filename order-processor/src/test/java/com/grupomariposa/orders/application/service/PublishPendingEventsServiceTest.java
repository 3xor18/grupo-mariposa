package com.grupomariposa.orders.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
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
    private static final String OWNER = "node-1";
    private static final RelaySettings SETTINGS = new RelaySettings(10, Duration.ofSeconds(30),
            Duration.ofMillis(50), Duration.ofSeconds(1), Duration.ofSeconds(60), OWNER);
    private static final PendingEvent EVENT =
            new PendingEvent("OUT-1", "ORD-1", "orders.processed.v1", "ORD-1", "{}", 2);
    private static final PendingEvent OTHER =
            new PendingEvent("OUT-2", "ORD-2", "orders.processed.v1", "ORD-2", "{}", 0);

    private final OutboxStore outbox = mock(OutboxStore.class);
    private final EventPublisher publisher = mock(EventPublisher.class);
    private final ProcessingObserver observer = mock(ProcessingObserver.class);
    private final PublishPendingEventsService service =
            new PublishPendingEventsService(outbox, publisher, () -> NOW, observer, SETTINGS);

    @BeforeEach
    void claimOne() {
        when(outbox.claim(10, NOW, NOW.plusSeconds(30), OWNER)).thenReturn(List.of(EVENT));
        when(outbox.markPublished(anyString(), eq(OWNER), any())).thenReturn(true);
        when(outbox.release(anyString(), eq(OWNER), anyInt(), any())).thenReturn(true);
    }

    @Test
    void should_mark_published_with_owner_fence_after_broker_ack() {
        when(publisher.publish(EVENT)).thenReturn(CompletableFuture.completedFuture(null));

        assertThat(service.publishPending()).isOne();
        verify(outbox).markPublished("OUT-1", OWNER, NOW);
        verify(observer).published(EVENT);
    }

    @Test
    void should_report_lost_lease_when_another_relay_owns_the_event() {
        when(publisher.publish(EVENT)).thenReturn(CompletableFuture.completedFuture(null));
        when(outbox.markPublished("OUT-1", OWNER, NOW)).thenReturn(false);

        assertThat(service.publishPending()).isZero();
        verify(observer).leaseLost(EVENT);
        verify(observer, never()).published(any());
    }

    @Test
    void should_release_with_backoff_when_send_fails() {
        when(publisher.publish(EVENT)).thenReturn(
                CompletableFuture.failedFuture(new IllegalStateException("broker down")));

        assertThat(service.publishPending()).isZero();
        verify(outbox).release("OUT-1", OWNER, 3, NOW.plusSeconds(4));
        verify(outbox, never()).markPublished(any(), any(), any());
        verify(observer).publicationFailed(eq(EVENT), any(IllegalStateException.class));
    }

    @Test
    void should_report_lost_lease_when_release_is_fenced() {
        when(publisher.publish(EVENT)).thenThrow(new IllegalStateException("serializer"));
        when(outbox.release("OUT-1", OWNER, 3, NOW.plusSeconds(4))).thenReturn(false);

        assertThat(service.publishPending()).isZero();
        verify(observer).leaseLost(EVENT);
    }

    @Test
    void should_settle_the_whole_batch_within_the_send_deadline() {
        when(outbox.claim(10, NOW, NOW.plusSeconds(30), OWNER))
                .thenReturn(List.of(EVENT, OTHER));
        final CompletableFuture<Void> neverAcknowledged = new CompletableFuture<>();
        when(publisher.publish(EVENT)).thenReturn(neverAcknowledged);
        when(publisher.publish(OTHER)).thenReturn(CompletableFuture.completedFuture(null));

        assertThat(service.publishPending()).isOne();
        assertThat(neverAcknowledged).isCancelled();
        verify(observer).publicationFailed(eq(EVENT), any(TimeoutException.class));
        verify(outbox).markPublished("OUT-2", OWNER, NOW);
    }

    @Test
    void should_isolate_store_failures_per_event() {
        when(outbox.claim(10, NOW, NOW.plusSeconds(30), OWNER))
                .thenReturn(List.of(EVENT, OTHER));
        when(publisher.publish(any())).thenReturn(CompletableFuture.completedFuture(null));
        when(outbox.markPublished("OUT-1", OWNER, NOW))
                .thenThrow(new IllegalStateException("mongo down"));

        assertThat(service.publishPending()).isOne();
        verify(observer).publicationFailed(eq(EVENT), any(IllegalStateException.class));
        verify(observer).published(OTHER);
    }

    @Test
    void should_survive_release_failures() {
        when(publisher.publish(EVENT)).thenReturn(
                CompletableFuture.failedFuture(new IllegalStateException("broker down")));
        when(outbox.release(anyString(), eq(OWNER), anyInt(), any()))
                .thenThrow(new IllegalStateException("mongo down"));

        assertThat(service.publishPending()).isZero();
    }

    @Test
    void should_release_and_keep_interrupt_flag_when_interrupted() {
        when(publisher.publish(EVENT)).thenReturn(new CompletableFuture<>());
        Thread.currentThread().interrupt();

        assertThat(service.publishPending()).isZero();
        assertThat(Thread.interrupted()).isTrue();
        verify(outbox).release("OUT-1", OWNER, 3, NOW.plusSeconds(4));
    }
}
