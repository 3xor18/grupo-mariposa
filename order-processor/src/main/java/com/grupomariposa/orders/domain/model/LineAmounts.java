package com.grupomariposa.orders.domain.model;

import java.util.Objects;

public record LineAmounts(
        Money grossSubtotal,
        Rate discountRate,
        Money discount,
        Money netSubtotal,
        Rate taxRate,
        Money taxAmount,
        Money lineTotal) {

    public LineAmounts {
        Objects.requireNonNull(grossSubtotal, "grossSubtotal");
        Objects.requireNonNull(discountRate, "discountRate");
        Objects.requireNonNull(discount, "discount");
        Objects.requireNonNull(netSubtotal, "netSubtotal");
        Objects.requireNonNull(taxRate, "taxRate");
        Objects.requireNonNull(taxAmount, "taxAmount");
        Objects.requireNonNull(lineTotal, "lineTotal");
    }
}
