package com.grupomariposa.orders.domain.model;

import java.time.Instant;
import java.util.Objects;

public record TaxRatePeriod(MarketCode market, TaxCategory category, Rate rate,
                            Instant validFrom, Instant validTo) {

    private static final String NOT_A_FRACTION = "Tax rate for %s %s must be between 0 and 1";
    private static final String EMPTY_PERIOD = "validTo must be after validFrom";

    public TaxRatePeriod {
        Objects.requireNonNull(market, "market");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(rate, "rate");
        Objects.requireNonNull(validFrom, "validFrom");
        if (!rate.isFraction()) {
            throw new IllegalArgumentException(NOT_A_FRACTION.formatted(market, category));
        }
        if (validTo != null && !validTo.isAfter(validFrom)) {
            throw new IllegalArgumentException(EMPTY_PERIOD);
        }
    }

    public boolean isOpenEnded() {
        return validTo == null;
    }

    public boolean covers(final Instant instant) {
        return !instant.isBefore(validFrom) && (isOpenEnded() || instant.isBefore(validTo));
    }

    public boolean sameKeyAs(final TaxRatePeriod other) {
        return market.equals(other.market) && category == other.category;
    }

    public boolean overlaps(final TaxRatePeriod other) {
        return sameKeyAs(other) && startsBeforeEndOf(other) && other.startsBeforeEndOf(this);
    }

    public TaxRatePeriod closedAt(final Instant end) {
        return new TaxRatePeriod(market, category, rate, validFrom, end);
    }

    private boolean startsBeforeEndOf(final TaxRatePeriod other) {
        return other.isOpenEnded() || validFrom.isBefore(other.validTo);
    }
}
