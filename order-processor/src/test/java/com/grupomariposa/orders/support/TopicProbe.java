package com.grupomariposa.orders.support;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;

public final class TopicProbe implements AutoCloseable {

    private static final Duration POLL = Duration.ofMillis(100);

    private final KafkaConsumer<String, byte[]> consumer;
    private final List<ConsumerRecord<String, byte[]>> seen = new ArrayList<>();

    public TopicProbe(final String bootstrapServers, final String topic) {
        this.consumer = new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false,
                ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed"),
                new StringDeserializer(), new ByteArrayDeserializer());
        final List<TopicPartition> partitions = consumer.partitionsFor(topic).stream()
                .map(info -> new TopicPartition(topic, info.partition()))
                .toList();
        consumer.assign(partitions);
        consumer.seekToBeginning(partitions);
    }

    public static String header(final ConsumerRecord<?, ?> record, final String name) {
        final Header header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }

    public List<ConsumerRecord<String, byte[]>> await(
            final Predicate<ConsumerRecord<String, byte[]>> filter, final int expected,
            final Duration timeout, final Duration settle) {
        final long deadline = System.nanoTime() + timeout.toNanos();
        while (matches(filter).size() < expected && System.nanoTime() < deadline) {
            poll();
        }
        final long settleDeadline = System.nanoTime() + settle.toNanos();
        while (System.nanoTime() < settleDeadline) {
            poll();
        }
        return matches(filter);
    }

    public List<ConsumerRecord<String, byte[]>> awaitKey(final String key, final int expected) {
        return await(record -> key.equals(record.key()), expected, Duration.ofSeconds(45),
                Duration.ofSeconds(2));
    }

    private void poll() {
        consumer.poll(POLL).forEach(seen::add);
    }

    private List<ConsumerRecord<String, byte[]>> matches(
            final Predicate<ConsumerRecord<String, byte[]>> filter) {
        return seen.stream().filter(filter).toList();
    }

    @Override
    public void close() {
        consumer.close();
    }
}
