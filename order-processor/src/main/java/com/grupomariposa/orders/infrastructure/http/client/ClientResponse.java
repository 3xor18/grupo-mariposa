package com.grupomariposa.orders.infrastructure.http.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ClientResponse(
        String clientId,
        String name,
        String status,
        String segment,
        String taxRegime,
        String market) {
}
