package com.grupomariposa.orders.application.port.out;

import java.util.concurrent.CompletableFuture;

public interface EventPublisher {

    CompletableFuture<Void> publish(PendingEvent event);
}
