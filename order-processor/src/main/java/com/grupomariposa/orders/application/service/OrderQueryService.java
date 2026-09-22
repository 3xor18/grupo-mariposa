package com.grupomariposa.orders.application.service;

import com.grupomariposa.orders.application.port.in.FindOrderQuery;
import com.grupomariposa.orders.application.port.in.ListOrdersQuery;
import com.grupomariposa.orders.application.port.out.OrderQueryRepository;
import com.grupomariposa.orders.application.query.OrderSearchCriteria;
import com.grupomariposa.orders.application.query.OrderSummary;
import com.grupomariposa.orders.application.query.PageResult;
import com.grupomariposa.orders.domain.model.Order;
import java.util.Objects;
import java.util.Optional;

public final class OrderQueryService implements FindOrderQuery, ListOrdersQuery {

    private final OrderQueryRepository repository;

    public OrderQueryService(final OrderQueryRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    @Override
    public Optional<Order> find(final String orderId) {
        return repository.findById(orderId);
    }

    @Override
    public PageResult<OrderSummary> list(final OrderSearchCriteria criteria) {
        return repository.search(criteria);
    }
}
