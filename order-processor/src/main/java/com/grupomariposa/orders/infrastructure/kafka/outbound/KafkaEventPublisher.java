package com.grupomariposa.orders.infrastructure.kafka.outbound;

import com.grupomariposa.orders.application.port.out.EventPublisher;
import com.grupomariposa.orders.application.port.out.PendingEvent;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;

public final class KafkaEventPublisher implements EventPublisher {

    public static final String EVENT_TYPE_HEADER = "eventType";
    public static final String EVENT_ID_HEADER = "eventId";
    public static final String EVENT_TYPE = "OrderProcessed";

    private final KafkaTemplate<String, String> template;

    public KafkaEventPublisher(final KafkaTemplate<String, String> template) {
        this.template = Objects.requireNonNull(template, "template");
    }

    @Override
    public CompletableFuture<Void> publish(final PendingEvent event) {
        final ProducerRecord<String, String> record =
                new ProducerRecord<>(event.topic(), event.key(), event.payload());
        record.headers().add(EVENT_TYPE_HEADER, EVENT_TYPE.getBytes(StandardCharsets.UTF_8));
        record.headers().add(EVENT_ID_HEADER, event.eventId().getBytes(StandardCharsets.UTF_8));
        return template.send(record).thenApply(result -> null);
    }
}
