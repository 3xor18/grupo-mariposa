package com.grupomariposa.orders.infrastructure.kafka.masterdata;

import java.util.Objects;

public record MasterDataListenerSettings(String clientsTopic, String productsTopic,
                                         String groupId, int concurrency,
                                         boolean autoStartup) {

    public static final String BEAN_NAME = "masterDataListenerSettings";
    public static final String CONTAINER_FACTORY = "masterDataListenerContainerFactory";

    public MasterDataListenerSettings {
        Objects.requireNonNull(clientsTopic, "clientsTopic");
        Objects.requireNonNull(productsTopic, "productsTopic");
        Objects.requireNonNull(groupId, "groupId");
    }
}
