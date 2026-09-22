package com.grupomariposa.orders.application.port.out;

import com.grupomariposa.orders.application.outcome.ProcessingOutcome;

public interface ProcessingObserver {

    void stage(ProcessingStage stage, String orderId, String eventId);

    void outcome(ProcessingOutcome outcome);

    void published(PendingEvent event);

    void publicationFailed(PendingEvent event, Throwable cause);
}
