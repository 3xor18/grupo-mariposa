package com.grupomariposa.orders.domain.model;

import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public record TaxRateSchedule(List<TaxRatePeriod> periods) {

    private static final String EMPTY = "The tax rate schedule needs at least one period";
    private static final String OVERLAP = "Tax rate periods overlap for %s %s from %s";
    private static final String UNCOVERED =
            "Tax rates for %s %s do not cover every instant from %s";
    private static final String NO_RATE = "No tax rate for %s %s at %s";

    public TaxRateSchedule {
        periods = List.copyOf(periods);
        if (periods.isEmpty()) {
            throw new IllegalArgumentException(EMPTY);
        }
        final List<TaxRatePeriod> sorted = sortedByStart(periods);
        for (int index = 0; index < sorted.size(); index++) {
            final TaxRatePeriod period = sorted.get(index);
            if (sorted.subList(index + 1, sorted.size()).stream().anyMatch(period::overlaps)) {
                throw new IllegalArgumentException(OVERLAP.formatted(period.market(),
                        period.category(), period.validFrom()));
            }
        }
    }

    public static boolean coversContinuously(final Collection<TaxRatePeriod> sameKey,
                                             final Instant from) {
        final List<TaxRatePeriod> sorted = sortedByStart(sameKey);
        if (sorted.isEmpty() || sorted.getFirst().validFrom().isAfter(from)
                || !sorted.getLast().isOpenEnded()) {
            return false;
        }
        for (int index = 1; index < sorted.size(); index++) {
            if (!sorted.get(index).validFrom().equals(sorted.get(index - 1).validTo())) {
                return false;
            }
        }
        return true;
    }

    public void requireCoverage(final Collection<MarketCode> markets, final Instant from) {
        for (final MarketCode market : markets) {
            for (final TaxCategory category : TaxCategory.values()) {
                if (!coversContinuously(periodsOf(market, category), from)) {
                    throw new IllegalArgumentException(
                            UNCOVERED.formatted(market, category, from));
                }
            }
        }
    }

    public AppliedTaxRates ratesAt(final MarketCode market, final Instant instant) {
        final Map<TaxCategory, Rate> rates = new EnumMap<>(TaxCategory.class);
        Instant effectiveFrom = Instant.MIN;
        for (final TaxCategory category : TaxCategory.values()) {
            final TaxRatePeriod period = periodsOf(market, category).stream()
                    .filter(candidate -> candidate.covers(instant))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            NO_RATE.formatted(market, category, instant)));
            rates.put(category, period.rate());
            if (period.validFrom().isAfter(effectiveFrom)) {
                effectiveFrom = period.validFrom();
            }
        }
        return new AppliedTaxRates(new TaxRateTable(Map.of(market, rates)), effectiveFrom);
    }

    public Instant earliestStart() {
        return sortedByStart(periods).getFirst().validFrom();
    }

    private List<TaxRatePeriod> periodsOf(final MarketCode market, final TaxCategory category) {
        return periods.stream()
                .filter(period -> period.market().equals(market) && period.category() == category)
                .toList();
    }

    private static List<TaxRatePeriod> sortedByStart(final Collection<TaxRatePeriod> periods) {
        return periods.stream().sorted(Comparator.comparing(TaxRatePeriod::validFrom)).toList();
    }
}
