package com.grupomariposa.orders.infrastructure.cache;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.grupomariposa.orders.domain.model.ProductProfile;
import com.grupomariposa.orders.domain.model.ProductStatus;
import com.grupomariposa.orders.domain.model.TaxCategory;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CachedProduct(String productId, String name, String sku, ProductStatus status,
                            TaxCategory taxCategory) {

    public static CachedProduct from(final ProductProfile profile) {
        return new CachedProduct(profile.productId(), profile.name(), profile.sku(),
                profile.status(), profile.taxCategory());
    }

    public boolean isComplete() {
        return productId != null && status != null && taxCategory != null;
    }

    public ProductProfile toProfile() {
        return new ProductProfile(productId, name, sku, status, taxCategory);
    }
}
