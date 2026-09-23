package com.grupomariposa.orders.application.service;

import static com.grupomariposa.orders.application.ApplicationFixtures.EVENT_ID;
import static com.grupomariposa.orders.application.ApplicationFixtures.ORDER_ID;
import static com.grupomariposa.orders.application.ApplicationFixtures.RECEIVED_AT;
import static com.grupomariposa.orders.application.ApplicationFixtures.goldenCommand;
import static com.grupomariposa.orders.domain.DomainFixtures.evaluator;
import static com.grupomariposa.orders.domain.DomainFixtures.goldenInput;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.grupomariposa.orders.application.command.OrderCommand;
import com.grupomariposa.orders.application.error.ExternalPermanentException;
import com.grupomariposa.orders.application.error.PersistenceException;
import com.grupomariposa.orders.application.outcome.ProcessingOutcome;
import com.grupomariposa.orders.application.port.out.IdGenerator;
import com.grupomariposa.orders.application.port.out.InboxEntry;
import com.grupomariposa.orders.application.port.out.InboxOutcome;
import com.grupomariposa.orders.application.port.out.OrderStore;
import com.grupomariposa.orders.application.port.out.ProcessingObserver;
import com.grupomariposa.orders.application.port.out.ProcessingStage;
import com.grupomariposa.orders.application.port.out.SaveResult;
import com.grupomariposa.orders.application.port.out.StoredOrderState;
import com.grupomariposa.orders.domain.DomainFixtures;
import com.grupomariposa.orders.domain.Markets;
import com.grupomariposa.orders.domain.model.EvaluationInput;
import com.grupomariposa.orders.domain.model.Lookup;
import com.grupomariposa.orders.domain.model.Money;
import com.grupomariposa.orders.domain.model.Order;
import com.grupomariposa.orders.domain.model.OrderStatus;
import com.grupomariposa.orders.domain.model.RejectionCode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProcessOrderServiceTest {

    private static final String OUTPUT_EVENT_ID = "OUT-1";
    private static final Instant NOW = Instant.parse("2026-09-18T15:42:12Z");

    private final OrderEnricher enricher = mock(OrderEnricher.class);
    private final OrderStore store = mock(OrderStore.class);
    private final IdGenerator ids = mock(IdGenerator.class);
    private final ProcessingObserver observer = mock(ProcessingObserver.class);
    private final OrderCommand command = goldenCommand();
    private ProcessOrderService service;

    @BeforeEach
    void setUp() {
        service = new ProcessOrderService(enricher, evaluator(), new OrderAssembler(() -> NOW,
                DomainFixtures.CURRENCIES),
                store, ids, observer, new VersionArbiter());
        when(ids.newEventId()).thenReturn(OUTPUT_EVENT_ID);
        when(store.findState(ORDER_ID)).thenReturn(Optional.empty());
    }

    @Test
    void should_persist_approved_order_with_outbox_event() {
        when(enricher.enrich(command)).thenReturn(goldenInput());
        when(store.save(any(), eq(OUTPUT_EVENT_ID))).thenReturn(new SaveResult.Saved());

        final ProcessingOutcome outcome = service.process(command);

        assertThat(outcome).isInstanceOfSatisfying(ProcessingOutcome.Processed.class, done -> {
            final Order order = done.order();
            assertThat(order.status()).isEqualTo(OrderStatus.APPROVED);
            assertThat(order.totals().grandTotal()).isEqualTo(Money.of("2100.11"));
            assertThat(order.processedAt()).isEqualTo(NOW);
            assertThat(order.timeline().receivedAt()).isEqualTo(RECEIVED_AT);
            assertThat(order.client().name()).isEqualTo("Distribuidora Central");
            assertThat(order.traceId()).isEqualTo("trace-1");
            assertThat(done.orderId()).isEqualTo(ORDER_ID);
            assertThat(done.eventId()).isEqualTo(EVENT_ID);
        });
        verify(observer).stage(ProcessingStage.ENRICHED, ORDER_ID, EVENT_ID);
        verify(observer).stage(ProcessingStage.EVALUATED, ORDER_ID, EVENT_ID);
        verify(observer).outcome(outcome);
    }

    @Test
    void should_persist_rejected_order_when_client_is_unknown() {
        final EvaluationInput golden = goldenInput();
        when(enricher.enrich(command)).thenReturn(
                new EvaluationInput(Markets.MX, DomainFixtures.digits(Markets.MX),
                        Lookup.notFound(), golden.items()));
        when(store.save(any(), eq(OUTPUT_EVENT_ID))).thenReturn(new SaveResult.Saved());

        final ProcessingOutcome outcome = service.process(command);

        assertThat(outcome).isInstanceOfSatisfying(ProcessingOutcome.Processed.class, done -> {
            assertThat(done.order().status()).isEqualTo(OrderStatus.REJECTED);
            assertThat(done.order().reason()).contains(RejectionCode.CLIENT_NOT_FOUND);
            assertThat(done.order().client().name()).isNull();
        });
    }

    @Test
    void should_short_circuit_duplicate_event_found_in_inbox() {
        when(store.inboxContains(EVENT_ID)).thenReturn(true);

        assertThat(service.process(command))
                .isEqualTo(new ProcessingOutcome.Duplicate(ORDER_ID, EVENT_ID));
        verifyNoInteractions(enricher);
        verify(store, never()).save(any(), any());
    }

    @Test
    void should_ignore_stale_version_and_record_it_in_inbox() {
        when(store.findState(ORDER_ID))
                .thenReturn(Optional.of(new StoredOrderState(3, "EVT-3", OrderStatus.APPROVED)));

        assertThat(service.process(command))
                .isEqualTo(new ProcessingOutcome.Stale(ORDER_ID, EVENT_ID, 1, 3));
        verify(store).recordInbox(new InboxEntry(EVENT_ID, ORDER_ID, 1, InboxOutcome.STALE,
                RECEIVED_AT));
        verifyNoInteractions(enricher);
    }

    @Test
    void should_detect_version_conflict_on_fast_path() {
        when(store.findState(ORDER_ID))
                .thenReturn(Optional.of(new StoredOrderState(1, "EVT-A", OrderStatus.REJECTED)));

        assertThat(service.process(command)).isEqualTo(
                new ProcessingOutcome.VersionConflict(ORDER_ID, EVENT_ID, 1, "EVT-A"));
        verify(store, never()).recordInbox(any());
    }

    @Test
    void should_reprocess_same_event_after_technical_failure() {
        when(store.findState(ORDER_ID)).thenReturn(Optional.of(
                new StoredOrderState(1, EVENT_ID, OrderStatus.TECHNICAL_FAILURE)));
        when(enricher.enrich(command)).thenReturn(goldenInput());
        when(store.save(any(), eq(OUTPUT_EVENT_ID))).thenReturn(new SaveResult.Saved());

        assertThat(service.process(command)).isInstanceOf(ProcessingOutcome.Processed.class);
    }

    @Test
    void should_report_duplicate_when_inbox_insert_loses_the_race() {
        when(enricher.enrich(command)).thenReturn(goldenInput());
        when(store.save(any(), eq(OUTPUT_EVENT_ID))).thenReturn(new SaveResult.DuplicateEvent());

        assertThat(service.process(command))
                .isEqualTo(new ProcessingOutcome.Duplicate(ORDER_ID, EVENT_ID));
    }

    @Test
    void should_classify_conflict_when_conditional_upsert_is_rejected() {
        when(enricher.enrich(command)).thenReturn(goldenInput());
        when(store.save(any(), eq(OUTPUT_EVENT_ID))).thenReturn(new SaveResult.Superseded(
                new StoredOrderState(1, "EVT-WINNER", OrderStatus.APPROVED)));

        assertThat(service.process(command)).isEqualTo(
                new ProcessingOutcome.VersionConflict(ORDER_ID, EVENT_ID, 1, "EVT-WINNER"));
    }

    @Test
    void should_classify_stale_when_higher_version_wins_during_processing() {
        when(enricher.enrich(command)).thenReturn(goldenInput());
        when(store.save(any(), eq(OUTPUT_EVENT_ID))).thenReturn(new SaveResult.Superseded(
                new StoredOrderState(2, "EVT-2", OrderStatus.APPROVED)));

        assertThat(service.process(command))
                .isEqualTo(new ProcessingOutcome.Stale(ORDER_ID, EVENT_ID, 1, 2));
        verify(store).recordInbox(any(InboxEntry.class));
    }

    @Test
    void should_fail_as_retryable_persistence_when_state_changes_inconsistently() {
        when(enricher.enrich(command)).thenReturn(goldenInput());
        when(store.save(any(), eq(OUTPUT_EVENT_ID))).thenReturn(new SaveResult.Superseded(
                new StoredOrderState(1, EVENT_ID, OrderStatus.TECHNICAL_FAILURE)));

        assertThatThrownBy(() -> service.process(command))
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    void should_propagate_dependency_failures_without_persisting() {
        when(enricher.enrich(command))
                .thenThrow(new ExternalPermanentException("clients-api", "401", null));

        assertThatThrownBy(() -> service.process(command))
                .isInstanceOf(ExternalPermanentException.class);
        verify(store, never()).save(any(), any());
        verify(observer, never()).outcome(any());
    }

    @Test
    void should_keep_line_order_of_command() {
        when(enricher.enrich(command)).thenReturn(goldenInput());
        when(store.save(any(), eq(OUTPUT_EVENT_ID))).thenReturn(new SaveResult.Saved());

        final Order order = ((ProcessingOutcome.Processed) service.process(command)).order();

        assertThat(order.lines()).extracting(line -> line.productId())
                .isEqualTo(List.of("PRD-001", "PRD-008"));
    }
}
