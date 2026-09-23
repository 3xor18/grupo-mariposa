package com.grupomariposa.orders.infrastructure.kafka.dlt;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Headers;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.support.KafkaHeaders;

public final class OrderDeadLetterPublisher extends DeadLetterPublishingRecoverer {

    private static final int ANY_PARTITION = -1;

    public OrderDeadLetterPublisher(final KafkaOperations<?, ?> template, final String topic,
                                    final DltHeadersFactory headersFactory) {
        super(template, (failed, exception) -> new TopicPartition(topic, ANY_PARTITION));
        setHeadersFunction(headersFactory::create);
        excludeHeader(HeaderNames.HeadersToAdd.EXCEPTION, HeaderNames.HeadersToAdd.EX_CAUSE,
                HeaderNames.HeadersToAdd.EX_MSG, HeaderNames.HeadersToAdd.EX_STACKTRACE);
    }

    @Override
    protected ProducerRecord<Object, Object> createProducerRecord(
            final ConsumerRecord<?, ?> consumerRecord, final TopicPartition topicPartition,
            final Headers headers, final byte[] key, final byte[] value) {
        headers.remove(KafkaHeaders.DELIVERY_ATTEMPT);
        final Integer partition = topicPartition.partition() < 0
                ? null : topicPartition.partition();
        final Long timestamp = consumerRecord.timestamp() < 0
                ? null : consumerRecord.timestamp();
        return new ProducerRecord<>(topicPartition.topic(), partition, timestamp,
                key == null ? consumerRecord.key() : key,
                value == null ? consumerRecord.value() : value,
                headers);
    }
}
