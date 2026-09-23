package com.grupomariposa.orders.domain;

import com.grupomariposa.orders.domain.model.MarketCode;

public final class Markets {

    public static final MarketCode MX = new MarketCode("MX");
    public static final MarketCode CO = new MarketCode("CO");
    public static final MarketCode PE = new MarketCode("PE");
    public static final MarketCode CL = new MarketCode("CL");
    public static final MarketCode EC = new MarketCode("EC");

    private Markets() {
    }
}
