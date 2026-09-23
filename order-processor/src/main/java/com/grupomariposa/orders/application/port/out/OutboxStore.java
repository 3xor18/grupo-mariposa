package com.grupomariposa.orders.application.port.out;

import java.time.Instant;
import java.util.List;

public interface OutboxStore {

    List<PendingEvent> claim(int limit, Instant now, Instant leaseUntil, String owner);

    boolean markPublished(String eventId, String owner, Instant publishedAt);

    boolean release(String eventId, String owner, int attempts, Instant availableAt);
}
