package com.grupomariposa.orders.application.port.out;

import com.grupomariposa.orders.domain.model.OrderStatus;
import java.util.Objects;

public record StoredOrderState(int eventVersion, String sourceEventId, OrderStatus status) {

    public StoredOrderState {
        Objects.requireNonNull(sourceEventId, "sourceEventId");
        Objects.requireNonNull(status, "status");
    }
}
