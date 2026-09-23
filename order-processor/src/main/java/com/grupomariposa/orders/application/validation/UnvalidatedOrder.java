package com.grupomariposa.orders.application.validation;

import java.util.List;

public record UnvalidatedOrder(
        String eventId,
        Integer eventVersion,
        String occurredAt,
        String orderId,
        String market,
        String currency,
        String clientId,
        String channel,
        List<UnvalidatedItem> items) {
}
