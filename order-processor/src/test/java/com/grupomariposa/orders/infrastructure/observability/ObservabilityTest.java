package com.grupomariposa.orders.infrastructure.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.grupomariposa.orders.application.outcome.ProcessingOutcome;
import com.grupomariposa.orders.application.port.out.PendingEvent;
import com.grupomariposa.orders.application.port.out.ProcessingObserver;
import com.grupomariposa.orders.application.port.out.ProcessingStage;
import com.grupomariposa.orders.infrastructure.persistence.MongoOutboxStore;
import com.grupomariposa.orders.infrastructure.persistence.PersistenceFixtures;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.dao.DataAccessResourceFailureException;

class ObservabilityTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }
    private final ProcessingObserver observer = new CompositeProcessingObserver(List.of(
            new MetricsProcessingObserver(registry),
            new LoggingProcessingObserver(new CauseSanitizer())));

    @Test
    void should_count_every_outcome_kind() {
        observer.outcome(new ProcessingOutcome.Processed(PersistenceFixtures.approvedOrder()));
        observer.outcome(new ProcessingOutcome.Processed(PersistenceFixtures.rejectedOrder()));
        observer.outcome(new ProcessingOutcome.Duplicate("O", "E"));
        observer.outcome(new ProcessingOutcome.Stale("O", "E", 1, 2));
        observer.outcome(new ProcessingOutcome.VersionConflict("O", "E", 1, "W"));
        observer.outcome(new ProcessingOutcome.TechnicalFailure("O", "E", "PERSISTENCE", true));

        assertThat(registry.counter(MetricsProcessingObserver.PROCESSED, "status", "APPROVED",
                "market", "MX").count()).isOne();
        assertThat(registry.counter(MetricsProcessingObserver.REJECTED, "reason",
                "CLIENT_NOT_FOUND", "market", "MX").count()).isOne();
        assertThat(registry.counter(MetricsProcessingObserver.AMOUNT, "currency", "MXN",
                "market", "MX").count()).isEqualTo(2100.11);
        assertThat(registry.counter(MetricsProcessingObserver.LINES, "market", "MX").count())
                .isEqualTo(2.0);
        assertThat(registry.counter(MetricsProcessingObserver.DUPLICATES).count()).isOne();
        assertThat(registry.counter(MetricsProcessingObserver.STALE).count()).isOne();
        assertThat(registry.counter(MetricsProcessingObserver.CONFLICTS).count()).isOne();
        assertThat(count(MetricsProcessingObserver.TECHNICAL_FAILURES, "category",
                "PERSISTENCE")).isOne();
    }

    @Test
    void should_count_every_stage_and_classify_terminal_ones() {
        observer.stage(ProcessingStage.RECEIVED, "O", "E");
        observer.stage(ProcessingStage.SENT_TO_DLT, "O", "E");

        assertThat(count(ProcessingMetrics.STAGES, ProcessingMetrics.STAGE, "RECEIVED")).isOne();
        assertThat(ProcessingStage.RECEIVED.isTerminal()).isFalse();
        assertThat(ProcessingStage.SENT_TO_DLT.isTerminal()).isTrue();
    }

    @Test
    void should_count_outbox_publications() {
        final PendingEvent event = new PendingEvent("OUT", "O", "t", "O", "{}", 0);

        observer.published(event);
        observer.publicationFailed(event, new IllegalStateException());
        observer.leaseLost(event);

        assertThat(registry.counter(MetricsProcessingObserver.OUTBOX_PUBLISHED).count()).isOne();
        assertThat(registry.counter(MetricsProcessingObserver.OUTBOX_FAILURES).count()).isOne();
        assertThat(registry.counter(MetricsProcessingObserver.OUTBOX_LEASE_LOST).count()).isOne();
    }

    @Test
    void should_restore_previous_log_context() {
        MDC.put(LogContext.ORDER_ID, "outer");
        try (LogContext ignored = LogContext.bind("inner", null)) {
            assertThat(MDC.get(LogContext.ORDER_ID)).isEqualTo("inner");
            assertThat(MDC.get(LogContext.EVENT_ID)).isNull();
        }
        assertThat(MDC.get(LogContext.ORDER_ID)).isEqualTo("outer");
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
        final TraceIds traces = new TraceIds(tracer);

        assertThat(traces.currentTraceId()).isEmpty();
        assertThat(traces.currentTraceId()).contains("abc");
        assertThat(traces.currentTraceId()).isEmpty();
    }

    private double count(final String name, final String tag, final String value) {
        return registry.counter(name, tag, value).count();
    }
}
