package com.grupomariposa.orders.application.query;

import com.grupomariposa.orders.domain.model.MarketCode;
import com.grupomariposa.orders.domain.model.OrderStatus;
import java.util.Optional;

public record OrderSearchCriteria(OrderStatus status, MarketCode market, int page, int size) {

    private static final String INVALID_PAGE = "Invalid page request";

    public OrderSearchCriteria {
        if (page < 0 || size < 1) {
            throw new IllegalArgumentException(INVALID_PAGE);
        }
    }

    public Optional<OrderStatus> statusFilter() {
        return Optional.ofNullable(status);
    }

    public Optional<MarketCode> marketFilter() {
        return Optional.ofNullable(market);
    }
}
