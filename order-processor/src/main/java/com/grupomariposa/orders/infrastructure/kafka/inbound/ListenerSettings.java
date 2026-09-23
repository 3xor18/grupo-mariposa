package com.grupomariposa.orders.infrastructure.kafka.inbound;

import java.util.Objects;

public record ListenerSettings(String topic, String groupId, int concurrency) {

    public static final String BEAN_NAME = "orderListenerSettings";

    public ListenerSettings {
        Objects.requireNonNull(topic, "topic");
        Objects.requireNonNull(groupId, "groupId");
    }
}
