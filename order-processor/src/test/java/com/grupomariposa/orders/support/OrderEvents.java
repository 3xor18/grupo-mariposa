package com.grupomariposa.orders.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class OrderEvents {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String EXAMPLE = "examples/orders.created.v1.approved.json";

    private final ObjectNode event;

    private OrderEvents(final ObjectNode event) {
        this.event = event;
    }

    public static OrderEvents golden() {
        return new OrderEvents((ObjectNode) Contracts.json(EXAMPLE).deepCopy());
    }

    public static OrderEvents goldenWithFreshIds(final String suffix) {
        return golden().orderId("ORD-IT-" + suffix).eventId("EVT-" + UUID.randomUUID());
    }

    public OrderEvents orderId(final String orderId) {
        event.put("orderId", orderId);
        return this;
    }

    public OrderEvents eventId(final String eventId) {
        event.put("eventId", eventId);
        return this;
    }

    public OrderEvents version(final int version) {
        event.put("eventVersion", version);
        return this;
    }

    public OrderEvents clientId(final String clientId) {
        event.put("clientId", clientId);
        return this;
    }

    public OrderEvents currency(final String currency) {
        event.put("currency", currency);
        return this;
    }

    public OrderEvents item(final int index, final String productId) {
        ((ObjectNode) event.withArray("items").get(index)).put("productId", productId);
        return this;
    }

    public OrderEvents fractionalQuantity(final int index) {
        ((ObjectNode) event.withArray("items").get(index)).put("quantity", 24.5);
        return this;
    }

    public OrderEvents singleItem(final String productId, final int quantity,
                                  final double unitPrice) {
        final ArrayNode items = event.putArray("items");
        items.addObject().put("productId", productId).put("quantity", quantity)
                .put("unitPrice", unitPrice);
        return this;
    }

    public String orderId() {
        return event.get("orderId").asText();
    }

    public String eventId() {
        return event.get("eventId").asText();
    }

    public JsonNode json() {
        return event;
    }

    public byte[] bytes() {
        return event.toString().getBytes(StandardCharsets.UTF_8);
    }
}
