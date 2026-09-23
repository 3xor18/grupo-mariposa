package com.grupomariposa.orders.application.validation;

import com.grupomariposa.orders.domain.model.CurrencyCode;
import com.grupomariposa.orders.domain.model.MarketCode;
import java.time.Instant;

record ValidatedHeader(MarketCode market, CurrencyCode currency, Instant occurredAt,
                       int eventVersion) {
}
