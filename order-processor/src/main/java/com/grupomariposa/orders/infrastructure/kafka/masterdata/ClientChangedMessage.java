package com.grupomariposa.orders.infrastructure.kafka.masterdata;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ClientChangedMessage(String eventId, String clientId, Long version, String status,
                                   String segment, String taxRegime, String market,
                                   String name) {
}
