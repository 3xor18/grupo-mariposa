package com.grupomariposa.orders.infrastructure.kafka.dlt;

import static com.grupomariposa.orders.application.ApplicationFixtures.EVENT_ID;
import static com.grupomariposa.orders.application.ApplicationFixtures.ORDER_ID;
import static com.grupomariposa.orders.application.ApplicationFixtures.goldenCommand;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.grupomariposa.orders.application.error.ErrorCategory;
import com.grupomariposa.orders.application.port.in.RecordTechnicalFailureUseCase;
import com.grupomariposa.orders.application.port.out.ProcessingObserver;
import com.grupomariposa.orders.application.port.out.ProcessingStage;
import com.grupomariposa.orders.domain.model.FailureDetails;
import com.grupomariposa.orders.infrastructure.kafka.inbound.MessageIds;
import com.grupomariposa.orders.infrastructure.kafka.inbound.RecordProcessingFailure;
import com.grupomariposa.orders.infrastructure.observability.CauseSanitizer;
import com.grupomariposa.orders.infrastructure.observability.ProcessingMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;

class DeadLetterRecovererTest {

    private final ConsumerRecordRecoverer deadLetters = mock(ConsumerRecordRecoverer.class);
    private final RecordTechnicalFailureUseCase technicalFailures =
            mock(RecordTechnicalFailureUseCase.class);
    private final ProcessingObserver observer = mock(ProcessingObserver.class);
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final DeadLetterRecoverer recoverer = new DeadLetterRecoverer(deadLetters,
            technicalFailures, observer, new ProcessingMetrics(registry), new CauseSanitizer());
    private final ConsumerRecord<String, byte[]> consumerRecord =
            new ConsumerRecord<>("orders.created.v1", 0, 1L, ORDER_ID, new byte[] {1});

    @Test
    void should_dead_letter_before_recording_technical_failure_of_dependency_errors() {
        final RecordProcessingFailure failure = failure(ErrorCategory.EXTERNAL_TRANSIENT, true);

        recoverer.accept(consumerRecord, failure);

        final InOrder order = inOrder(deadLetters, technicalFailures);
        order.verify(deadLetters).accept(eq(consumerRecord), any(DescribedFailure.class));
        order.verify(technicalFailures).record(any(), any());

        verify(technicalFailures).record(goldenCommand(),
                new FailureDetails("EXTERNAL_TRANSIENT", "products-api responded 503", 1));
        verify(deadLetters).accept(eq(consumerRecord), any(DescribedFailure.class));
        verify(observer).stage(ProcessingStage.SENT_TO_DLT, ORDER_ID, EVENT_ID);
        assertThat(registry.counter(ProcessingMetrics.DEAD_LETTERED, ProcessingMetrics.CATEGORY,
                "EXTERNAL_TRANSIENT").count()).isOne();
    }

    @Test
    void should_never_persist_contract_violations() {
        recoverer.accept(consumerRecord, failure(ErrorCategory.VALIDATION, false));

        verify(technicalFailures, never()).record(any(), any());
        verify(deadLetters).accept(eq(consumerRecord), any());
    }

    @Test
    void should_skip_recording_when_no_command_is_available() {
        recoverer.accept(consumerRecord, failure(ErrorCategory.PERSISTENCE, false));

        verify(technicalFailures, never()).record(any(), any());
    }

    @Test
    void should_still_dead_letter_when_mongo_is_unreachable() {
        when(technicalFailures.record(any(), any()))
                .thenThrow(new DataAccessResourceFailureException("down"));
        final RecordProcessingFailure failure = failure(ErrorCategory.PERSISTENCE, true);

        recoverer.accept(consumerRecord, failure);

        verify(deadLetters).accept(eq(consumerRecord), any(DescribedFailure.class));
    }

    @Test
    void should_rethrow_without_recording_when_dead_letter_topic_is_unavailable() {
        final RecordProcessingFailure failure = failure(ErrorCategory.EXTERNAL_TRANSIENT, true);
        doThrow(new IllegalStateException("broker down")).when(deadLetters)
                .accept(eq(consumerRecord), any());

        assertThatThrownBy(() -> recoverer.accept(consumerRecord, failure))
                .isInstanceOf(IllegalStateException.class);
        verify(observer, never()).stage(ProcessingStage.SENT_TO_DLT, ORDER_ID, EVENT_ID);
        verify(technicalFailures, never()).record(any(), any());
    }

    @Test
    void should_dead_letter_and_record_once_when_redelivered_after_dlt_failure() {
        final RecordProcessingFailure failure = failure(ErrorCategory.EXTERNAL_TRANSIENT, true);
        doThrow(new IllegalStateException("broker down")).doNothing().when(deadLetters)
                .accept(eq(consumerRecord), any());

        assertThatThrownBy(() -> recoverer.accept(consumerRecord, failure))
                .isInstanceOf(IllegalStateException.class);
        recoverer.accept(consumerRecord, failure);

        verify(deadLetters, times(2)).accept(eq(consumerRecord), any(DescribedFailure.class));
        verify(technicalFailures, times(1)).record(any(), any());
        verify(observer, times(1)).stage(ProcessingStage.SENT_TO_DLT, ORDER_ID, EVENT_ID);
    }

    private static RecordProcessingFailure failure(final ErrorCategory category,
                                                   final boolean withCommand) {
        return RecordProcessingFailure.of(category, "products-api responded 503",
                new MessageIds(ORDER_ID, EVENT_ID), withCommand ? goldenCommand() : null, null);
    }
}
