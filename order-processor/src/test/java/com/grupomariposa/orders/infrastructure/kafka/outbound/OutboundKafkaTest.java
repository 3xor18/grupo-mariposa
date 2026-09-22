package com.grupomariposa.orders.infrastructure.kafka.outbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.grupomariposa.orders.application.port.in.PublishPendingEventsUseCase;
import com.grupomariposa.orders.application.port.out.PendingEvent;
import com.grupomariposa.orders.infrastructure.observability.CauseSanitizer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

class OutboundKafkaTest {

    @Test
    @SuppressWarnings("unchecked")
    void should_publish_payload_keyed_by_order_with_event_headers() {
        final KafkaTemplate<String, String> template = mock(KafkaTemplate.class);
        when(template.send(any(ProducerRecord.class))).thenReturn(
                CompletableFuture.completedFuture(mock(SendResult.class)));
        final PendingEvent event =
                new PendingEvent("OUT-1", "ORD-1", "orders.processed.v1", "ORD-1", "{}", 0);

        assertThat(new KafkaEventPublisher(template).publish(event)).isCompleted();

        final ArgumentCaptor<ProducerRecord<String, String>> sent =
                ArgumentCaptor.forClass(ProducerRecord.class);
        verify(template).send(sent.capture());
        assertThat(sent.getValue().key()).isEqualTo("ORD-1");
        assertThat(sent.getValue().topic()).isEqualTo("orders.processed.v1");
        assertThat(new String(sent.getValue().headers()
                .lastHeader(KafkaEventPublisher.EVENT_TYPE_HEADER).value(),
                StandardCharsets.UTF_8)).isEqualTo(KafkaEventPublisher.EVENT_TYPE);
    }

    @Test
    void should_swallow_relay_cycle_failures() {
        final PublishPendingEventsUseCase relay = mock(PublishPendingEventsUseCase.class);
        when(relay.publishPending()).thenThrow(new IllegalStateException("mongo down"))
                .thenReturn(1);
        final SimpleMeterRegistry registry = new SimpleMeterRegistry();
        final OutboxRelayScheduler scheduler =
                new OutboxRelayScheduler(relay, new CauseSanitizer(), registry);

        assertThatCode(scheduler::relay).doesNotThrowAnyException();
        assertThatCode(scheduler::relay).doesNotThrowAnyException();

        verify(relay, times(2)).publishPending();
        assertThat(registry.counter(OutboxRelayScheduler.RELAY_FAILURES).count()).isOne();
    }
}
