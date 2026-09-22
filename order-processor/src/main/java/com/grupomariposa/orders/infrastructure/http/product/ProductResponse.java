package com.grupomariposa.orders.infrastructure.http.product;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ProductResponse(
        String productId,
        String name,
        String sku,
        String status,
        String taxCategory) {
}
