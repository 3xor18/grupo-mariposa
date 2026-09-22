package com.grupomariposa.orders.application.service;

import static com.grupomariposa.orders.application.ApplicationFixtures.EVENT_ID;
import static com.grupomariposa.orders.application.ApplicationFixtures.ORDER_ID;
import static com.grupomariposa.orders.application.ApplicationFixtures.goldenCommand;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.grupomariposa.orders.application.outcome.ProcessingOutcome;
import com.grupomariposa.orders.application.port.out.OrderStore;
import com.grupomariposa.orders.application.port.out.ProcessingObserver;
import com.grupomariposa.orders.domain.model.FailureDetails;
import com.grupomariposa.orders.domain.model.Order;
import com.grupomariposa.orders.domain.model.OrderStatus;
import com.grupomariposa.orders.domain.model.Totals;
import java.time.Instant;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

class RecordTechnicalFailureServiceTest {

    private final OrderStore store = mock(OrderStore.class);
    private final ProcessingObserver observer = mock(ProcessingObserver.class);
    private final RecordTechnicalFailureService service = new RecordTechnicalFailureService(
            new OrderAssembler(() -> Instant.EPOCH), store, observer);

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void should_store_technical_failure_without_pricing(final boolean recorded) {
        final FailureDetails failure = new FailureDetails("EXTERNAL_TRANSIENT", "503", 4);
        when(store.saveTechnicalFailure(any())).thenReturn(recorded);

        final ProcessingOutcome.TechnicalFailure outcome =
                service.record(goldenCommand(), failure);

        assertThat(outcome).isEqualTo(new ProcessingOutcome.TechnicalFailure(ORDER_ID, EVENT_ID,
                "EXTERNAL_TRANSIENT", recorded));
        final ArgumentCaptor<Order> stored = ArgumentCaptor.forClass(Order.class);
        verify(store).saveTechnicalFailure(stored.capture());
        assertThat(stored.getValue().status()).isEqualTo(OrderStatus.TECHNICAL_FAILURE);
        assertThat(stored.getValue().totals()).isEqualTo(Totals.ZERO);
        assertThat(stored.getValue().failureDetails()).contains(failure);
        assertThat(stored.getValue().lines()).allSatisfy(line ->
                assertThat(line.pricing()).isEmpty());
        verify(observer).outcome(outcome);
    }
}
