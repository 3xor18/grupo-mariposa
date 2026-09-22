package com.grupomariposa.orders.infrastructure.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.grupomariposa.orders.application.outcome.ProcessingOutcome;
import com.grupomariposa.orders.application.port.out.PendingEvent;
import com.grupomariposa.orders.infrastructure.persistence.MongoOutboxStore;
import com.grupomariposa.orders.infrastructure.persistence.PersistenceFixtures;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.dao.DataAccessResourceFailureException;

class ObservabilityTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final LoggingProcessingObserver observer = new LoggingProcessingObserver(registry);

    @Test
    void should_count_every_outcome_kind() {
        observer.outcome(new ProcessingOutcome.Processed(PersistenceFixtures.approvedOrder()));
        observer.outcome(new ProcessingOutcome.Processed(PersistenceFixtures.rejectedOrder()));
        observer.outcome(new ProcessingOutcome.Duplicate("O", "E"));
        observer.outcome(new ProcessingOutcome.Stale("O", "E", 1, 2));
        observer.outcome(new ProcessingOutcome.VersionConflict("O", "E", 1, "W"));
        observer.outcome(new ProcessingOutcome.TechnicalFailure("O", "E", "PERSISTENCE", true));

        assertThat(count(LoggingProcessingObserver.PROCESSED, "status", "APPROVED")).isOne();
        assertThat(count(LoggingProcessingObserver.REJECTED, "reason", "CLIENT_NOT_FOUND"))
                .isOne();
        assertThat(registry.counter(LoggingProcessingObserver.DUPLICATES).count()).isOne();
        assertThat(registry.counter(LoggingProcessingObserver.STALE).count()).isOne();
        assertThat(registry.counter(LoggingProcessingObserver.CONFLICTS).count()).isOne();
        assertThat(count(LoggingProcessingObserver.TECHNICAL_FAILURES, "category",
                "PERSISTENCE")).isOne();
    }

    @Test
    void should_count_outbox_publications() {
        final PendingEvent event = new PendingEvent("OUT", "O", "t", "O", "{}", 0);

        observer.published(event);
        observer.publicationFailed(event, new IllegalStateException());
        observer.leaseLost(event);

        assertThat(registry.counter(LoggingProcessingObserver.OUTBOX_PUBLISHED).count()).isOne();
        assertThat(registry.counter(LoggingProcessingObserver.OUTBOX_FAILURES).count()).isOne();
        assertThat(registry.counter(LoggingProcessingObserver.OUTBOX_LEASE_LOST).count()).isOne();
    }

    @Test
    void should_restore_previous_log_context() {
        MDC.put(LogContext.ORDER_ID, "outer");
        try (LogContext ignored = LogContext.bind("inner", null)) {
            assertThat(MDC.get(LogContext.ORDER_ID)).isEqualTo("inner");
            assertThat(MDC.get(LogContext.EVENT_ID)).isNull();
        }
        assertThat(MDC.get(LogContext.ORDER_ID)).isEqualTo("outer");
        MDC.clear();
    }

    @Test
    void should_record_latency_retries_and_dead_letters() {
        final ProcessingMetrics metrics = new ProcessingMetrics(registry);

        metrics.stop(metrics.start());
        metrics.retried("clients-api");

        assertThat(registry.timer(ProcessingMetrics.LATENCY).count()).isOne();
        assertThat(count(ProcessingMetrics.RETRIES, "dependency", "clients-api")).isOne();
    }

    @Test
    void should_expose_outbox_gauges_and_degrade_to_nan() {
        final MongoOutboxStore store = mock(MongoOutboxStore.class);
        final Instant now = Instant.parse("2026-09-22T10:00:10Z");
        when(store.countUnpublished()).thenReturn(3L)
                .thenThrow(new DataAccessResourceFailureException("down"));
        when(store.oldestUnpublishedCreatedAt())
                .thenReturn(Optional.of(now.minusSeconds(4)), Optional.empty());
        new OutboxMetrics(store, Clock.fixed(now, ZoneOffset.UTC)).bindTo(registry);

        assertThat(registry.get(OutboxMetrics.PENDING).gauge().value()).isEqualTo(3.0);
        assertThat(registry.get(OutboxMetrics.PENDING).gauge().value()).isNaN();
        assertThat(registry.get(OutboxMetrics.OLDEST_AGE).gauge().value()).isEqualTo(4.0);
        assertThat(registry.get(OutboxMetrics.OLDEST_AGE).gauge().value()).isZero();
    }

    @Test
    void should_read_current_trace_id_when_present() {
        final Tracer tracer = mock(Tracer.class);
        final Span span = mock(Span.class);
        final TraceContext context = mock(TraceContext.class);
        when(tracer.currentSpan()).thenReturn(null, span, span);
        when(span.context()).thenReturn(context);
        when(context.traceId()).thenReturn("abc", "");
        final com.grupomariposa.orders.infrastructure.observability.TraceContext traces =
                new com.grupomariposa.orders.infrastructure.observability.TraceContext(tracer);

        assertThat(traces.currentTraceId()).isEmpty();
        assertThat(traces.currentTraceId()).contains("abc");
        assertThat(traces.currentTraceId()).isEmpty();
    }

    private double count(final String name, final String tag, final String value) {
        return registry.counter(name, tag, value).count();
    }
}
