package com.grupomariposa.orders.infrastructure.persistence.document;

public final class Fields {

    public static final String ID = "_id";
    public static final String EVENT_VERSION = "eventVersion";
    public static final String SOURCE_EVENT_ID = "sourceEventId";
    public static final String STATUS = "status";
    public static final String MARKET = "market";
    public static final String PROCESSED_AT = "processedAt";
    public static final String RECEIVED_AT = "receivedAt";
    public static final String CLIENT_ID = "client.clientId";
    public static final String CREATED_AT = "createdAt";
    public static final String PUBLISHED_AT = "publishedAt";
    public static final String AVAILABLE_AT = "availableAt";
    public static final String LEASE_UNTIL = "leaseUntil";
    public static final String LEASE_OWNER = "leaseOwner";
    public static final String ATTEMPTS = "attempts";
    public static final String ORDER_ID = "orderId";

    private Fields() {
    }
}
