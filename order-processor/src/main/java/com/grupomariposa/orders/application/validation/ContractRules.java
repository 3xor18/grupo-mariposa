package com.grupomariposa.orders.application.validation;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.regex.Pattern;

public record ContractRules(
        Pattern productIdPattern,
        Pattern clientIdPattern,
        int maxPricePrecision,
        int maxPriceScale,
        BigDecimal maxPrice) {

    private static final String INVALID_LIMITS =
            "Price precision must be positive, scale non-negative and maximum price positive";

    public ContractRules {
        Objects.requireNonNull(productIdPattern, "productIdPattern");
        Objects.requireNonNull(clientIdPattern, "clientIdPattern");
        Objects.requireNonNull(maxPrice, "maxPrice");
        if (maxPricePrecision < 1 || maxPriceScale < 0 || maxPrice.signum() <= 0) {
            throw new IllegalArgumentException(INVALID_LIMITS);
        }
    }
}
