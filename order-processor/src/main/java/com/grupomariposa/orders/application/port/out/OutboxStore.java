package com.grupomariposa.orders.application.port.out;

import java.time.Instant;
import java.util.List;

public interface OutboxStore {

    List<PendingEvent> claim(int limit, Instant now, Instant leaseUntil, String owner);

    void markPublished(String eventId, Instant publishedAt);

    void release(String eventId, int attempts, Instant availableAt);
}
