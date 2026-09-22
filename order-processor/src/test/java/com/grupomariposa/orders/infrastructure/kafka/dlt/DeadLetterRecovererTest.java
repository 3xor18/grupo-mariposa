package com.grupomariposa.orders.infrastructure.kafka.dlt;

import static com.grupomariposa.orders.application.ApplicationFixtures.EVENT_ID;
import static com.grupomariposa.orders.application.ApplicationFixtures.ORDER_ID;
import static com.grupomariposa.orders.application.ApplicationFixtures.goldenCommand;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.grupomariposa.orders.application.error.ErrorCategory;
import com.grupomariposa.orders.application.port.in.RecordTechnicalFailureUseCase;
import com.grupomariposa.orders.application.port.out.ProcessingObserver;
import com.grupomariposa.orders.application.port.out.ProcessingStage;
import com.grupomariposa.orders.domain.model.FailureDetails;
import com.grupomariposa.orders.infrastructure.kafka.inbound.MessageIds;
import com.grupomariposa.orders.infrastructure.kafka.inbound.RecordProcessingFailure;
import com.grupomariposa.orders.infrastructure.observability.ProcessingMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
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
    private final ConsumerRecord<String, byte[]> record =
            new ConsumerRecord<>("orders.created.v1", 0, 1L, ORDER_ID, new byte[] {1});

    @Test
    void should_record_technical_failure_before_dead_lettering_dependency_errors() {
        final RecordProcessingFailure failure = failure(ErrorCategory.EXTERNAL_TRANSIENT, true);

        recoverer.accept(record, failure);

        verify(technicalFailures).record(goldenCommand(),
                new FailureDetails("EXTERNAL_TRANSIENT", "products-api responded 503", 1));
        verify(deadLetters).accept(record, failure);
        verify(observer).stage(ProcessingStage.SENT_TO_DLT, ORDER_ID, EVENT_ID);
        assertThat(registry.counter(ProcessingMetrics.DEAD_LETTERED, ProcessingMetrics.CATEGORY,
                "EXTERNAL_TRANSIENT").count()).isOne();
    }

    @Test
    void should_never_persist_contract_violations() {
        recoverer.accept(record, failure(ErrorCategory.VALIDATION, false));

        verify(technicalFailures, never()).record(any(), any());
        verify(deadLetters).accept(eq(record), any());
    }

    @Test
    void should_skip_recording_when_no_command_is_available() {
        recoverer.accept(record, failure(ErrorCategory.PERSISTENCE, false));

        verify(technicalFailures, never()).record(any(), any());
    }

    @Test
    void should_still_dead_letter_when_mongo_is_unreachable() {
        when(technicalFailures.record(any(), any()))
                .thenThrow(new DataAccessResourceFailureException("down"));
        final RecordProcessingFailure failure = failure(ErrorCategory.PERSISTENCE, true);

        recoverer.accept(record, failure);

        verify(deadLetters).accept(record, failure);
    }

    private static RecordProcessingFailure failure(final ErrorCategory category,
                                                   final boolean withCommand) {
        return RecordProcessingFailure.of(category, "products-api responded 503",
                new MessageIds(ORDER_ID, EVENT_ID), withCommand ? goldenCommand() : null, null);
    }
}
