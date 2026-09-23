package com.grupomariposa.orders.domain.model;

import java.util.Objects;

public final class TaxRateRuleViolation extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final TaxRateRule rule;

    public TaxRateRuleViolation(final TaxRateRule rule, final String message) {
        super(message);
        this.rule = Objects.requireNonNull(rule, "rule");
    }

    public TaxRateRule rule() {
        return rule;
    }
}
