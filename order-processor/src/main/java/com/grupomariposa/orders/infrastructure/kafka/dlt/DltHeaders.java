package com.grupomariposa.orders.infrastructure.kafka.dlt;

public final class DltHeaders {

    public static final String ERROR_CATEGORY = "x-error-category";
    public static final String ERROR_CAUSE = "x-error-cause";
    public static final String ATTEMPTS = "x-attempts";
    public static final String FAILED_AT = "x-failed-at";
    public static final String COMPONENT = "x-component";
    public static final String ORDER_ID = "x-order-id";
    public static final String EVENT_ID = "x-event-id";
    public static final String COMPONENT_NAME = "order-processor";

    private DltHeaders() {
    }
}
