package com.grupomariposa.orders.application.validation;

import java.math.BigDecimal;
import java.util.Objects;

final class PriceRule {

    private static final String NON_NEGATIVE = "must be greater than or equal to 0";
    private static final String TOO_LARGE = "must not exceed %s";
    private static final String TOO_MANY_DECIMALS = "must have at most %d decimal places";
    private static final String TOO_MANY_DIGITS = "must have at most %d significant digits";

    private final ContractRules rules;

    PriceRule(final ContractRules rules) {
        this.rules = Objects.requireNonNull(rules, "rules");
    }

    void check(final String field, final BigDecimal price, final ErrorCollector errors) {
        if (!errors.requirePresent(field, price)) {
            return;
        }
        if (price.signum() < 0) {
            errors.add(field, NON_NEGATIVE);
        } else if (price.compareTo(rules.maxPrice()) > 0) {
            errors.add(field, TOO_LARGE.formatted(rules.maxPrice().toPlainString()));
        } else {
            checkShape(field, price.stripTrailingZeros(), errors);
        }
    }

    private void checkShape(final String field, final BigDecimal price,
                            final ErrorCollector errors) {
        if (price.scale() > rules.maxPriceScale()) {
            errors.add(field, TOO_MANY_DECIMALS.formatted(rules.maxPriceScale()));
        } else if (price.precision() > rules.maxPricePrecision()) {
            errors.add(field, TOO_MANY_DIGITS.formatted(rules.maxPricePrecision()));
        }
    }
}
