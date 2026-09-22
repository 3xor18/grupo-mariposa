package com.grupomariposa.orders.infrastructure.persistence.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grupomariposa.orders.domain.model.Order;
import com.grupomariposa.orders.domain.model.RejectionCode;
import com.grupomariposa.orders.domain.model.Totals;
import java.util.Objects;

public final class OutboxPayloadFactory {

    private final ObjectMapper objectMapper;

    public OutboxPayloadFactory(final ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    public String create(final Order order, final String eventId) {
        try {
            return objectMapper.writeValueAsString(payload(order, eventId));
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("Outbox payload could not be serialized", failure);
        }
    }

    public OrderProcessedPayload payload(final Order order, final String eventId) {
        return new OrderProcessedPayload(eventId, order.eventVersion(), order.processedAt(),
                order.sourceEventId(), order.orderId(), order.client().clientId(),
                order.status().name(), order.market().name(), order.currency().name(),
                totals(order.totals()), order.reason().map(RejectionCode::name).orElse(null),
                order.violations().stream()
                        .map(violation -> new OrderProcessedPayload.ViolationPayload(
                                violation.code().name(), violation.message(),
                                violation.productId()))
                        .toList());
    }

    private static OrderProcessedPayload.TotalsPayload totals(final Totals totals) {
        return new OrderProcessedPayload.TotalsPayload(totals.grossSubtotal().amount(),
                totals.discount().amount(), totals.netSubtotal().amount(),
                totals.tax().amount(), totals.grandTotal().amount());
    }
}
