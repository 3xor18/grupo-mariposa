package com.grupomariposa.orders.application.command;

import com.grupomariposa.orders.domain.model.CurrencyCode;
import com.grupomariposa.orders.domain.model.MarketCode;
import com.grupomariposa.orders.domain.model.RequestedItem;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record OrderCommand(
        String eventId,
        int eventVersion,
        String orderId,
        MarketCode market,
        CurrencyCode currency,
        String clientId,
        String channel,
        Instant occurredAt,
        List<RequestedItem> items,
        Reception reception) {

    public OrderCommand {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(market, "market");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(clientId, "clientId");
        Objects.requireNonNull(reception, "reception");
        items = List.copyOf(items);
    }
}
