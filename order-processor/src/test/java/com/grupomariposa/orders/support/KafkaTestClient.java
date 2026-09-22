package com.grupomariposa.orders.support;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

public final class KafkaTestClient implements AutoCloseable {

    private static final Duration POLL = Duration.ofMillis(200);
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

    public void send(final String topic, final String key, final byte[] value) {
        try {
            producer.send(new ProducerRecord<>(topic, key, value))
                    .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        } catch (ExecutionException | TimeoutException failure) {
            throw new IllegalStateException(failure);
        }
    }

    public List<ConsumerRecord<String, byte[]>> readAll(final String topic,
                                                        final Predicate<ConsumerRecord<String,
                                                                byte[]>> filter,
                                                        final Duration window) {
        final List<ConsumerRecord<String, byte[]>> matches = new ArrayList<>();
        try (KafkaConsumer<String, byte[]> consumer = consumer()) {
            consumer.subscribe(List.of(topic));
            final long deadline = System.nanoTime() + window.toNanos();
            while (System.nanoTime() < deadline) {
                consumer.poll(POLL).forEach(record -> {
                    if (filter.test(record)) {
                        matches.add(record);
                    }
                });
            }
        }
        return matches;
    }

    private KafkaConsumer<String, byte[]> consumer() {
        return new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ConsumerConfig.GROUP_ID_CONFIG, "it-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed"),
                new StringDeserializer(), new ByteArrayDeserializer());
    }

    @Override
    public void close() {
        producer.close();
    }
}
