package com.grupomariposa.orders.domain.model;

import java.util.Objects;

public record ResolvedItem(RequestedItem item, Lookup<ProductProfile> product) {

    public ResolvedItem {
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(product, "product");
    }
}
