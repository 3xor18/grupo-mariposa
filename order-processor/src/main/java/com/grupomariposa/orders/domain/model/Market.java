package com.grupomariposa.orders.domain.model;

import java.util.Arrays;
import java.util.Optional;

public enum Market {
    MX(Currency.MXN, TaxSchedule.ofPercentages(16, 8, 0)),
    CO(Currency.COP, TaxSchedule.ofPercentages(19, 5, 0)),
    PE(Currency.PEN, TaxSchedule.ofPercentages(18, 10, 0));

    private final Currency currency;
    private final TaxSchedule taxSchedule;

    Market(final Currency currency, final TaxSchedule taxSchedule) {
        this.currency = currency;
        this.taxSchedule = taxSchedule;
    }

    public static Optional<Market> fromCode(final String code) {
        return Arrays.stream(values()).filter(market -> market.name().equals(code)).findFirst();
    }

    public Currency currency() {
        return currency;
    }

    public boolean acceptsCurrency(final Currency candidate) {
        return currency == candidate;
    }

    public Rate taxRateFor(final TaxCategory category) {
        return taxSchedule.rateFor(category);
    }
}
