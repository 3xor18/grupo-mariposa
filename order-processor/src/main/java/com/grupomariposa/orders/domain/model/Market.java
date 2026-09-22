package com.grupomariposa.orders.domain.model;

import java.util.Arrays;
import java.util.Optional;

public enum Market {
    MX,
    CO,
    PE;

    public static Optional<Market> fromCode(final String code) {
        return Arrays.stream(values()).filter(market -> market.name().equals(code)).findFirst();
    }
}
