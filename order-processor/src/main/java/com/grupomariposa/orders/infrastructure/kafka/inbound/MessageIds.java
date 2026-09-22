package com.grupomariposa.orders.infrastructure.kafka.inbound;

import com.fasterxml.jackson.databind.JsonNode;

public record MessageIds(String orderId, String eventId) {

    public static final MessageIds UNKNOWN = new MessageIds(null, null);
    private static final String ORDER_ID = "orderId";
    private static final String EVENT_ID = "eventId";

    public static MessageIds from(final JsonNode tree) {
        return new MessageIds(text(tree, ORDER_ID), text(tree, EVENT_ID));
    }

    private static String text(final JsonNode tree, final String field) {
        final JsonNode node = tree.get(field);
        return node != null && node.isTextual() ? node.textValue() : null;
    }
}
