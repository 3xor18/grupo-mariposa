package com.grupomariposa.orders.application.query;

import com.grupomariposa.orders.domain.model.CurrencyCode;
import com.grupomariposa.orders.domain.model.MarketCode;
import com.grupomariposa.orders.domain.model.Money;
import com.grupomariposa.orders.domain.model.OrderStatus;
import com.grupomariposa.orders.domain.model.RejectionCode;
import java.time.Instant;

public record OrderSummary(
        String orderId,
        OrderStatus status,
        MarketCode market,
        CurrencyCode currency,
        String clientId,
        int eventVersion,
        Money grandTotal,
        RejectionCode reason,
        Instant processedAt) {
}
