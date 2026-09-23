package com.grupomariposa.orders.infrastructure.observability;

import org.slf4j.MDC;

public final class LogContext implements AutoCloseable {

    public static final String ORDER_ID = "orderId";
    public static final String EVENT_ID = "eventId";

    private final String previousOrderId;
    private final String previousEventId;

    private LogContext(final String orderId, final String eventId) {
        this.previousOrderId = MDC.get(ORDER_ID);
        this.previousEventId = MDC.get(EVENT_ID);
        put(ORDER_ID, orderId);
        put(EVENT_ID, eventId);
    }

    public static LogContext bind(final String orderId, final String eventId) {
        return new LogContext(orderId, eventId);
    }

    @Override
    public void close() {
        put(ORDER_ID, previousOrderId);
        put(EVENT_ID, previousEventId);
    }

    private static void put(final String key, final String value) {
        if (value == null) {
            MDC.remove(key);
        } else {
            MDC.put(key, value);
        }
    }
}
