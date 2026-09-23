package com.grupomariposa.orders.domain;

import com.grupomariposa.orders.domain.model.CurrencyCode;

public final class Currencies {

    public static final CurrencyCode MXN = new CurrencyCode("MXN");
    public static final CurrencyCode COP = new CurrencyCode("COP");
    public static final CurrencyCode PEN = new CurrencyCode("PEN");
    public static final CurrencyCode CLP = new CurrencyCode("CLP");
    public static final CurrencyCode USD = new CurrencyCode("USD");

    private Currencies() {
    }
}
