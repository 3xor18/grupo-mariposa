package mariposa.e2e;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

public final class KafkaGateway {

    private static final String ALL_ACKS = "all";
    private static final long SEND_TIMEOUT_SECONDS = 10;
    private static final Duration METADATA_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration POLL_INTERVAL = Duration.ofMillis(200);
    private static final String KEY_FIELD = "key";
    private static final String VALUE_FIELD = "value";
    private static final String HEADERS_FIELD = "headers";

    private final String bootstrapServers;

    public KafkaGateway(String bootstrapServers) {
        this.bootstrapServers = bootstrapServers;
    }

    public void publish(String topic, String key, String payload)
            throws InterruptedException, ExecutionException, TimeoutException {
        try (var producer = new KafkaProducer<String, String>(producerProperties())) {
            producer.send(new ProducerRecord<>(topic, key, payload))
                    .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
    }

    public Mark mark(String topic) {
        try (var consumer = new KafkaConsumer<String, String>(consumerProperties())) {
            var partitions = consumer.partitionsFor(topic, METADATA_TIMEOUT).stream()
                    .map(info -> new TopicPartition(topic, info.partition()))
                    .toList();
            return new Mark(topic, Map.copyOf(consumer.endOffsets(partitions, METADATA_TIMEOUT)));
        }
    }

    public List<Map<String, Object>> readAfter(
            Mark mark, String key, int expectedCount, long timeoutMillis) {
        var matches = new ArrayList<Map<String, Object>>();
        try (var consumer = new KafkaConsumer<String, String>(consumerProperties())) {
            consumer.assign(mark.offsets().keySet());
            mark.offsets().forEach(consumer::seek);
            var deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
            while (matches.size() < expectedCount && System.nanoTime() < deadline) {
                consumer.poll(POLL_INTERVAL).forEach(record -> collect(record, key, matches));
            }
        }
        return matches;
    }

    private static void collect(
            ConsumerRecord<String, String> record, String key, List<Map<String, Object>> sink) {
        if (key.equals(record.key())) {
            sink.add(toMap(record));
        }
    }

    private static Map<String, Object> toMap(ConsumerRecord<String, String> record) {
        var headers = new HashMap<String, String>();
        for (Header header : record.headers()) {
            headers.put(header.key(), new String(header.value(), StandardCharsets.UTF_8));
        }
        return Map.of(KEY_FIELD, record.key(), VALUE_FIELD, record.value(), HEADERS_FIELD, headers);
    }

    private Properties producerProperties() {
        var properties = new Properties();
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        properties.put(ProducerConfig.ACKS_CONFIG, ALL_ACKS);
        return properties;
    }

    private Properties consumerProperties() {
        var properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        return properties;
    }

    public record Mark(String topic, Map<TopicPartition, Long> offsets) {
    }
}
