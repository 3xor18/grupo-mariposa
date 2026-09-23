package com.grupomariposa.orders.infrastructure.kafka.inbound;

import com.grupomariposa.orders.application.validation.UnvalidatedItem;
import com.grupomariposa.orders.application.validation.UnvalidatedOrder;
import java.util.List;

public final class OrderMessageMapper {

    public UnvalidatedOrder toUnvalidated(final OrderCreatedMessage message) {
        return new UnvalidatedOrder(message.eventId(), message.eventVersion(),
                message.occurredAt(), message.orderId(), message.market(), message.currency(),
                message.clientId(), message.channel(), items(message.items()));
    }

    private static List<UnvalidatedItem> items(final List<OrderItemMessage> items) {
        if (items == null) {
            return null;
        }
        return items.stream()
                .map(item -> item == null ? null
                        : new UnvalidatedItem(item.productId(), item.quantity(), item.unitPrice()))
                .toList();
    }
}
