package com.grupomariposa.orders.application.query;

import com.grupomariposa.orders.domain.model.MarketCode;
import com.grupomariposa.orders.domain.model.TaxCategory;
import com.grupomariposa.orders.domain.model.TaxRateStatus;

public record TaxRateFilter(MarketCode market, TaxCategory category, TaxRateStatus status) {
}
