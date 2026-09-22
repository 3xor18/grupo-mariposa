package com.grupomariposa.orders.infrastructure.kafka.dlt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.support.KafkaHeaders;

class OrderDeadLetterPublisherTest {

    private final OrderDeadLetterPublisher publisher = new OrderDeadLetterPublisher(
            mock(KafkaOperations.class), "orders.processing.dlt",
            new DltHeadersFactory(Clock.systemUTC(), new CauseSanitizer()));
    private final ConsumerRecord<String, byte[]> record =
            new ConsumerRecord<>("orders.created.v1", 2, 7L, "original-key", new byte[] {9, 8});

    @Test
    void should_key_by_extracted_order_and_drop_delivery_attempt_header() {
        final Headers headers = new RecordHeaders();
        headers.add(DltHeaders.ORDER_ID, "ORD-7".getBytes(StandardCharsets.UTF_8));
        headers.add(KafkaHeaders.DELIVERY_ATTEMPT, new byte[] {0, 0, 0, 2});

        final ProducerRecord<Object, Object> produced = publisher.createProducerRecord(record,
                new TopicPartition("orders.processing.dlt", -1), headers, null, null);

        assertThat(produced.key()).isEqualTo("ORD-7");
        assertThat(produced.partition()).isNull();
        assertThat(produced.value()).isEqualTo(new byte[] {9, 8});
        assertThat(produced.headers().lastHeader(KafkaHeaders.DELIVERY_ATTEMPT)).isNull();
    }

    @Test
    void should_keep_original_key_and_partition_when_given() {
        final ProducerRecord<Object, Object> produced = publisher.createProducerRecord(record,
                new TopicPartition("orders.processing.dlt", 1), new RecordHeaders(), null,
                new byte[] {5});

        assertThat(produced.key()).isEqualTo("original-key");
        assertThat(produced.partition()).isOne();
        assertThat(produced.value()).isEqualTo(new byte[] {5});
    }
}
