package com.grupomariposa.orders.application.port.out;

import com.grupomariposa.orders.domain.model.Order;
import java.util.Optional;

public interface OrderStore {

    boolean inboxContains(String eventId);

    Optional<StoredOrderState> findState(String orderId);

    SaveResult save(Order order, String outboxEventId);

    boolean saveTechnicalFailure(Order order);

    void recordInbox(InboxEntry entry);
}
