package com.grupomariposa.orders.application.service;

import static com.grupomariposa.orders.application.ApplicationFixtures.EVENT_ID;
import static com.grupomariposa.orders.application.ApplicationFixtures.ORDER_ID;
import static com.grupomariposa.orders.application.ApplicationFixtures.command;
import static org.assertj.core.api.Assertions.assertThat;

import com.grupomariposa.orders.application.outcome.ProcessingOutcome;
import com.grupomariposa.orders.application.port.out.StoredOrderState;
import com.grupomariposa.orders.domain.model.OrderStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class VersionArbiterTest {

    private final VersionArbiter arbiter = new VersionArbiter();

    @Test
    void should_mark_lower_incoming_version_as_stale() {
        assertThat(arbiter.classify(command("EVT-1", 1),
                new StoredOrderState(2, "EVT-2", OrderStatus.APPROVED)))
                .contains(new ProcessingOutcome.Stale(ORDER_ID, "EVT-1", 1, 2));
    }

    @ParameterizedTest
    @EnumSource(OrderStatus.class)
    void should_proceed_when_incoming_version_is_higher(final OrderStatus status) {
        assertThat(arbiter.classify(command("EVT-3", 3),
                new StoredOrderState(2, "EVT-2", status))).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(OrderStatus.class)
    void should_flag_conflict_when_same_version_has_another_event(final OrderStatus status) {
        assertThat(arbiter.classify(command("EVT-B", 2),
                new StoredOrderState(2, "EVT-A", status)))
                .contains(new ProcessingOutcome.VersionConflict(ORDER_ID, "EVT-B", 2, "EVT-A"));
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"APPROVED", "REJECTED"})
    void should_flag_duplicate_when_same_event_already_decided(final OrderStatus status) {
        assertThat(arbiter.classify(command(EVENT_ID, 1),
                new StoredOrderState(1, EVENT_ID, status)))
                .contains(new ProcessingOutcome.Duplicate(ORDER_ID, EVENT_ID));
    }

    @Test
    void should_allow_replay_of_same_event_after_technical_failure() {
        assertThat(arbiter.classify(command(EVENT_ID, 1),
                new StoredOrderState(1, EVENT_ID, OrderStatus.TECHNICAL_FAILURE))).isEmpty();
    }
}
