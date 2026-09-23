package com.grupomariposa.orders.infrastructure.kafka.inbound;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderCreatedMessage(
        String eventId,
        Integer eventVersion,
        String occurredAt,
        String orderId,
        String market,
        String currency,
        String clientId,
        String channel,
        List<OrderItemMessage> items) {
}
