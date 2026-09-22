package com.grupomariposa.orders.application.query;

import com.grupomariposa.orders.domain.model.Currency;
import com.grupomariposa.orders.domain.model.Market;
import com.grupomariposa.orders.domain.model.Money;
import com.grupomariposa.orders.domain.model.OrderStatus;
import com.grupomariposa.orders.domain.model.RejectionCode;
import java.time.Instant;

public record OrderSummary(
        String orderId,
        OrderStatus status,
        Market market,
        Currency currency,
        String clientId,
        int eventVersion,
        Money grandTotal,
        RejectionCode reason,
        Instant processedAt) {
}
