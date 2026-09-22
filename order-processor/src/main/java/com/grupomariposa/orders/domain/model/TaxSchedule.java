package com.grupomariposa.orders.domain.model;

import java.util.Objects;

public record TaxSchedule(Rate standard, Rate reduced, Rate exempt) {

    public TaxSchedule {
        Objects.requireNonNull(standard, "standard");
        Objects.requireNonNull(reduced, "reduced");
        Objects.requireNonNull(exempt, "exempt");
    }

    public static TaxSchedule ofPercentages(final int standard, final int reduced,
                                            final int exempt) {
        return new TaxSchedule(Rate.ofPercent(standard), Rate.ofPercent(reduced),
                Rate.ofPercent(exempt));
    }

    public Rate rateFor(final TaxCategory category) {
        return switch (category) {
            case STANDARD -> standard;
            case REDUCED -> reduced;
            case EXEMPT -> exempt;
        };
    }
}
