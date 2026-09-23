package com.grupomariposa.orders.infrastructure.kafka.inbound;

import static com.grupomariposa.orders.application.ApplicationFixtures.EVENT_ID;
import static com.grupomariposa.orders.application.ApplicationFixtures.ORDER_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.grupomariposa.orders.application.ApplicationFixtures;
import com.grupomariposa.orders.application.command.OrderCommand;
import com.grupomariposa.orders.application.error.ErrorCategory;
import com.grupomariposa.orders.application.error.ExternalPermanentException;
import com.grupomariposa.orders.application.error.ExternalTransientException;
import com.grupomariposa.orders.application.outcome.ProcessingOutcome;
import com.grupomariposa.orders.application.port.in.ProcessOrderUseCase;
import com.grupomariposa.orders.application.port.out.ProcessingObserver;
import com.grupomariposa.orders.application.port.out.ProcessingStage;
import com.grupomariposa.orders.infrastructure.observability.ProcessingMetrics;
import com.grupomariposa.orders.infrastructure.observability.TraceIds;
import com.grupomariposa.orders.support.Contracts;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class OrderCreatedListenerTest {

    private static final byte[] GOLDEN =
            Contracts.bytes("examples/orders.created.v1.approved.json");

    private final ProcessOrderUseCase useCase = mock(ProcessOrderUseCase.class);
    private final ProcessingObserver observer = mock(ProcessingObserver.class);
    private final TraceIds traceIds = mock(TraceIds.class);
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private OrderCreatedListener listener;

    @BeforeEach
    void setUp() {
        when(traceIds.currentTraceId()).thenReturn(Optional.of("trace-9"));
        listener = new OrderCreatedListener(new OrderMessageReader(), new OrderMessageMapper(),
                ApplicationFixtures.validator(), useCase, observer,
                () -> Instant.EPOCH,
                traceIds, new ProcessingMetrics(registry));
    }

    @Test
    void should_validate_and_process_valid_records() {
        when(useCase.process(any())).thenReturn(new ProcessingOutcome.Duplicate(ORDER_ID,
                EVENT_ID));

        listener.onMessage(consumerRecord(GOLDEN));

        final ArgumentCaptor<OrderCommand> command = ArgumentCaptor.forClass(OrderCommand.class);
        verify(useCase).process(command.capture());
        assertThat(command.getValue().reception().traceId()).isEqualTo("trace-9");
        verify(observer).stage(ProcessingStage.RECEIVED, ORDER_ID, EVENT_ID);
        verify(observer).stage(ProcessingStage.VALIDATED, ORDER_ID, EVENT_ID);
        assertThat(registry.timer(ProcessingMetrics.LATENCY).count()).isOne();
    }

    @Test
    void should_fail_validation_without_calling_use_case() {
        final byte[] invalid = new String(GOLDEN, StandardCharsets.UTF_8)
                .replace("\"MXN\"", "\"PEN\"").getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> listener.onMessage(consumerRecord(invalid)))
                .isExactlyInstanceOf(RecordProcessingFailure.class)
                .satisfies(failure -> assertThat(((RecordProcessingFailure) failure).category())
                        .isEqualTo(ErrorCategory.VALIDATION));
        verify(useCase, never()).process(any());
    }

    @Test
    void should_reject_markets_outside_the_catalog_as_validation_errors() {
        final byte[] argentina = new String(GOLDEN, StandardCharsets.UTF_8)
                .replace("\"MX\"", "\"AR\"").replace("\"MXN\"", "\"ARS\"")
                .getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> listener.onMessage(consumerRecord(argentina)))
                .isInstanceOfSatisfying(RecordProcessingFailure.class, failure -> {
                    assertThat(failure.category()).isEqualTo(ErrorCategory.VALIDATION);
                    assertThat(failure.getMessage()).contains("market");
                });
        verify(useCase, never()).process(any());
    }

    @Test
    void should_reject_astronomic_prices_as_validation_errors() {
        final byte[] huge = new String(GOLDEN, StandardCharsets.UTF_8)
                .replace("35.5", "1e999999999").getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> listener.onMessage(consumerRecord(huge)))
                .isInstanceOfSatisfying(RecordProcessingFailure.class, failure -> {
                    assertThat(failure.category()).isEqualTo(ErrorCategory.VALIDATION);
                    assertThat(failure.getMessage()).contains("items[0].unitPrice");
                });
        verify(useCase, never()).process(any());
    }

    @Test
    void should_turn_version_conflicts_into_non_retryable_failures() {
        when(useCase.process(any())).thenReturn(new ProcessingOutcome.VersionConflict(ORDER_ID,
                EVENT_ID, 1, "EVT-WINNER"));

        assertThatThrownBy(() -> listener.onMessage(consumerRecord(GOLDEN)))
                .isExactlyInstanceOf(RecordProcessingFailure.class)
                .hasMessageContaining("EVT-WINNER");
    }

    @Test
    void should_mark_transient_failures_as_retryable_and_keep_command() {
        when(useCase.process(any()))
                .thenThrow(new ExternalTransientException("products-api", "503", null));

        assertThatThrownBy(() -> listener.onMessage(consumerRecord(GOLDEN)))
                .isInstanceOfSatisfying(RetryableRecordFailure.class, failure -> {
                    assertThat(failure.category()).isEqualTo(ErrorCategory.EXTERNAL_TRANSIENT);
                    assertThat(failure.command()).isPresent();
                });
    }

    @Test
    void should_keep_permanent_failures_non_retryable() {
        when(useCase.process(any()))
                .thenThrow(new ExternalPermanentException("clients-api", "401", null));

        assertThatThrownBy(() -> listener.onMessage(consumerRecord(GOLDEN)))
                .isExactlyInstanceOf(RecordProcessingFailure.class);
    }

    @Test
    void should_classify_unexpected_errors() {
        when(useCase.process(any())).thenThrow(new IllegalStateException("bug"));

        assertThatThrownBy(() -> listener.onMessage(consumerRecord(GOLDEN)))
                .isInstanceOfSatisfying(RecordProcessingFailure.class, failure -> {
                    assertThat(failure.category()).isEqualTo(ErrorCategory.UNEXPECTED);
                    assertThat(failure.getMessage()).isEqualTo("IllegalStateException");
                });
    }

    private static ConsumerRecord<String, byte[]> consumerRecord(final byte[] value) {
        return new ConsumerRecord<>("orders.created.v1", 0, 0L, ORDER_ID, value);
    }
}
