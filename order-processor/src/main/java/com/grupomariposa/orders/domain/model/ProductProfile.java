package com.grupomariposa.orders.domain.model;

import java.util.Objects;

public record ProductProfile(
        String productId,
        String name,
        String sku,
        ProductStatus status,
        TaxCategory taxCategory) {

    public ProductProfile {
        Objects.requireNonNull(productId, "productId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(taxCategory, "taxCategory");
    }

    public boolean isActive() {
        return status == ProductStatus.ACTIVE;
    }
}
