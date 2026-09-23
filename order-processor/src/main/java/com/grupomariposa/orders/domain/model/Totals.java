package com.grupomariposa.orders.domain.model;

import java.util.Collection;
import java.util.Objects;

public record Totals(
        Money grossSubtotal,
        Money discount,
        Money netSubtotal,
        Money tax,
        Money grandTotal) {

    public Totals {
        Objects.requireNonNull(grossSubtotal, "grossSubtotal");
        Objects.requireNonNull(discount, "discount");
        Objects.requireNonNull(netSubtotal, "netSubtotal");
        Objects.requireNonNull(tax, "tax");
        Objects.requireNonNull(grandTotal, "grandTotal");
    }

    public static Totals zero(final int fractionDigits) {
        final Money zero = Money.zero(fractionDigits);
        return new Totals(zero, zero, zero, zero, zero);
    }

    public static Totals sumOf(final Collection<LineAmounts> lines, final int fractionDigits) {
        return lines.stream().map(Totals::of).reduce(zero(fractionDigits), Totals::plus);
    }

    private static Totals of(final LineAmounts line) {
        return new Totals(line.grossSubtotal(), line.discount(), line.netSubtotal(),
                line.taxAmount(), line.lineTotal());
    }

    private Totals plus(final Totals other) {
        return new Totals(
                grossSubtotal.plus(other.grossSubtotal),
                discount.plus(other.discount),
                netSubtotal.plus(other.netSubtotal),
                tax.plus(other.tax),
                grandTotal.plus(other.grandTotal));
    }
}
