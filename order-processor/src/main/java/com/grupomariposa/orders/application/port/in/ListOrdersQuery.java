package com.grupomariposa.orders.application.port.in;

import com.grupomariposa.orders.application.query.OrderSearchCriteria;
import com.grupomariposa.orders.application.query.OrderSummary;
import com.grupomariposa.orders.application.query.PageResult;

public interface ListOrdersQuery {

    PageResult<OrderSummary> list(OrderSearchCriteria criteria);
}
