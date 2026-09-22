package com.grupomariposa.orders.infrastructure.kafka.dlt;

import java.util.Map;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

public final class DeadLetterProducer implements AutoCloseable {

    private final ProducerFactory<Object, Object> factory;
    private final KafkaTemplate<Object, Object> template;

    public DeadLetterProducer(final ProducerFactory<Object, Object> baseFactory) {
        this.factory = baseFactory.copyWithConfigurationOverride(Map.of(
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class));
        this.template = new KafkaTemplate<>(factory);
    }

    public KafkaTemplate<Object, Object> template() {
        return template;
    }

    @Override
    public void close() {
        factory.reset();
    }
}
