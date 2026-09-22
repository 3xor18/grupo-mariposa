package com.grupomariposa.orders.application.port.in;

import com.grupomariposa.orders.domain.model.Order;
import java.util.Optional;

public interface FindOrderQuery {

    Optional<Order> find(String orderId);
}
