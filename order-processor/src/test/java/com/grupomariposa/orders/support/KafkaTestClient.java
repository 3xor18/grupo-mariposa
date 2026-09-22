package com.grupomariposa.orders.support;

import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;

public final class KafkaTestClient implements AutoCloseable {

    private static final long SEND_TIMEOUT_SECONDS = 10;

    private final String bootstrapServers;
    private final KafkaProducer<String, byte[]> producer;

    public KafkaTestClient(final String bootstrapServers) {
        this.bootstrapServers = bootstrapServers;
        this.producer = new KafkaProducer<>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ProducerConfig.ACKS_CONFIG, "all"),
                new StringSerializer(), new ByteArraySerializer());
    }

    public RecordMetadata send(final String topic, final String key, final byte[] value) {
        try {
            return producer.send(new ProducerRecord<>(topic, key, value))
                    .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        } catch (ExecutionException | TimeoutException failure) {
            throw new IllegalStateException(failure);
        }
    }

    @Override
    public void close() {
        producer.close();
    }
}
