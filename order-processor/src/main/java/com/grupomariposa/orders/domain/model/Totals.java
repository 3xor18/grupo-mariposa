package com.grupomariposa.orders.domain.model;

import java.util.Collection;
import java.util.Objects;

public record Totals(
        Money grossSubtotal,
        Money discount,
        Money netSubtotal,
        Money tax,
        Money grandTotal) {

    public static final Totals ZERO =
            new Totals(Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO);

    public Totals {
        Objects.requireNonNull(grossSubtotal, "grossSubtotal");
        Objects.requireNonNull(discount, "discount");
        Objects.requireNonNull(netSubtotal, "netSubtotal");
        Objects.requireNonNull(tax, "tax");
        Objects.requireNonNull(grandTotal, "grandTotal");
    }

    public static Totals sumOf(final Collection<LineAmounts> lines) {
        Totals totals = ZERO;
        for (final LineAmounts line : lines) {
            totals = totals.plus(line);
        }
        return totals;
    }

    private Totals plus(final LineAmounts line) {
        return new Totals(
                grossSubtotal.plus(line.grossSubtotal()),
                discount.plus(line.discount()),
                netSubtotal.plus(line.netSubtotal()),
                tax.plus(line.taxAmount()),
                grandTotal.plus(line.lineTotal()));
    }
}
