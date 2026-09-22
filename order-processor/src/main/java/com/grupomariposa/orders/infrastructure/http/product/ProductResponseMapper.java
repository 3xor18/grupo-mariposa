package com.grupomariposa.orders.infrastructure.http.product;

import com.grupomariposa.orders.domain.model.ProductProfile;
import com.grupomariposa.orders.domain.model.ProductStatus;
import com.grupomariposa.orders.domain.model.TaxCategory;
import com.grupomariposa.orders.infrastructure.http.Dependency;
import com.grupomariposa.orders.infrastructure.http.ResponseFields;

public final class ProductResponseMapper {

    private static final String PRODUCT_ID = "productId";
    private static final String STATUS = "status";
    private static final String TAX_CATEGORY = "taxCategory";

    private final ResponseFields fields = new ResponseFields(Dependency.PRODUCTS_API);

    public ProductProfile toProfile(final ProductResponse response) {
        return new ProductProfile(fields.requireText(PRODUCT_ID, response.productId()),
                response.name(), response.sku(),
                fields.parse(ProductStatus.class, STATUS, response.status()),
                fields.parse(TaxCategory.class, TAX_CATEGORY, response.taxCategory()));
    }
}
