package com.grupomariposa.orders.infrastructure.kafka.masterdata;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ProductChangedMessage(String eventId, String productId, String market,
                                    Long version, String status, String taxCategory,
                                    String name, String sku) {
}
