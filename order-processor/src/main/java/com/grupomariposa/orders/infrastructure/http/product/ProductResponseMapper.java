package com.grupomariposa.orders.infrastructure.http.product;

import com.grupomariposa.orders.application.error.ExternalPermanentException;
import com.grupomariposa.orders.domain.model.ProductProfile;
import com.grupomariposa.orders.domain.model.ProductStatus;
import com.grupomariposa.orders.domain.model.TaxCategory;
import com.grupomariposa.orders.infrastructure.http.Dependency;
import com.grupomariposa.orders.infrastructure.http.EnumParser;

public final class ProductResponseMapper {

    private static final String MISSING_ID = "products-api returned a product without id";
    private static final String STATUS = "status";
    private static final String TAX_CATEGORY = "taxCategory";

    private final EnumParser parser = new EnumParser(Dependency.PRODUCTS_API);

    public ProductProfile toProfile(final ProductResponse response) {
        if (response.productId() == null || response.productId().isBlank()) {
            throw new ExternalPermanentException(Dependency.PRODUCTS_API.id(), MISSING_ID, null);
        }
        return new ProductProfile(response.productId(), response.name(), response.sku(),
                parser.parse(ProductStatus.class, STATUS, response.status()),
                parser.parse(TaxCategory.class, TAX_CATEGORY, response.taxCategory()));
    }
}
