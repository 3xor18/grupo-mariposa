package com.grupomariposa.orders.infrastructure.kafka.dlt;

import java.nio.ByteBuffer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.support.KafkaHeaders;

public final class DeliveryAttempts {

    private static final int FIRST_ATTEMPT = 1;

    private DeliveryAttempts() {
    }

    public static int of(final ConsumerRecord<?, ?> consumerRecord) {
        final Header header = consumerRecord.headers().lastHeader(KafkaHeaders.DELIVERY_ATTEMPT);
        if (header == null || header.value() == null || header.value().length != Integer.BYTES) {
            return FIRST_ATTEMPT;
        }
        return Math.max(FIRST_ATTEMPT, ByteBuffer.wrap(header.value()).getInt());
    }
}
