package com.grupomariposa.orders.application.query;

import com.grupomariposa.orders.domain.model.Market;
import com.grupomariposa.orders.domain.model.OrderStatus;
import java.util.Optional;

public record OrderSearchCriteria(OrderStatus status, Market market, int page, int size) {

    public static final int DEFAULT_PAGE = 0;
    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    public OrderSearchCriteria {
        if (page < 0 || size < 1 || size > MAX_SIZE) {
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
