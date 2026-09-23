package com.grupomariposa.orders.domain.model;

import java.math.BigDecimal;
import java.util.Objects;

public record OrderLine(
        String productId,
        String name,
        String sku,
        TaxCategory taxCategory,
        int quantity,
        BigDecimal unitPrice,
        LineAmounts amounts) {

    public OrderLine {
        Objects.requireNonNull(productId, "productId");
        Objects.requireNonNull(unitPrice, "unitPrice");
    }

    public static OrderLine priced(final RequestedItem item, final ProductProfile product,
                                   final LineAmounts amounts) {
        return new OrderLine(item.productId(), product.name(), product.sku(),
                product.taxCategory(), item.quantity(), item.unitPrice(), amounts);
    }

    public static OrderLine unpriced(final RequestedItem item,
                                     final Lookup<ProductProfile> product) {
        return product.value()
                .map(found -> new OrderLine(item.productId(), found.name(), found.sku(),
                        found.taxCategory(), item.quantity(), item.unitPrice(), null))
                .orElseGet(() -> unpriced(item));
    }

    public static OrderLine unpriced(final RequestedItem item) {
        return new OrderLine(item.productId(), null, null, null, item.quantity(),
                item.unitPrice(), null);
    }
}
