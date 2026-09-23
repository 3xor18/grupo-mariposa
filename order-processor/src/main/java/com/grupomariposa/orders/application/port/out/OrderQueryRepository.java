package com.grupomariposa.orders.application.port.out;

import com.grupomariposa.orders.application.query.OrderSearchCriteria;
import com.grupomariposa.orders.application.query.OrderSummary;
import com.grupomariposa.orders.application.query.PageResult;
import com.grupomariposa.orders.domain.model.Order;
import java.util.Optional;

public interface OrderQueryRepository {

    Optional<Order> findById(String orderId);

    PageResult<OrderSummary> search(OrderSearchCriteria criteria);
}
