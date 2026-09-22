package com.grupomariposa.orders.application.query;

import com.grupomariposa.orders.domain.model.Market;
import com.grupomariposa.orders.domain.model.OrderStatus;
import java.util.Optional;

public record OrderSearchCriteria(OrderStatus status, Market market, int page, int size) {

    public OrderSearchCriteria {
        if (page < 0 || size < 1) {
            throw new IllegalArgumentException("Invalid page request");
        }
    }

    public Optional<OrderStatus> statusFilter() {
        return Optional.ofNullable(status);
    }

    public Optional<Market> marketFilter() {
        return Optional.ofNullable(market);
    }
}
