package com.grupomariposa.orders.application.validation;

import com.grupomariposa.orders.domain.model.Currency;
import com.grupomariposa.orders.domain.model.Market;
import java.time.Instant;

record ValidatedHeader(Market market, Currency currency, Instant occurredAt, int eventVersion) {
}
