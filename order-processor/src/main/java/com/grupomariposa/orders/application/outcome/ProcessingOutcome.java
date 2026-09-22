package com.grupomariposa.orders.application.outcome;

import com.grupomariposa.orders.domain.model.Order;

public sealed interface ProcessingOutcome permits ProcessingOutcome.Processed,
        ProcessingOutcome.Duplicate, ProcessingOutcome.Stale, ProcessingOutcome.VersionConflict,
        ProcessingOutcome.TechnicalFailure {

    String orderId();

    String eventId();

    record Processed(Order order) implements ProcessingOutcome {

        @Override
        public String orderId() {
            return order.orderId();
        }

        @Override
        public String eventId() {
            return order.sourceEventId();
        }
    }

    record Duplicate(String orderId, String eventId) implements ProcessingOutcome {
    }

    record Stale(String orderId, String eventId, int incomingVersion, int storedVersion)
            implements ProcessingOutcome {
    }

    record VersionConflict(String orderId, String eventId, int version, String winningEventId)
            implements ProcessingOutcome {
    }

    record TechnicalFailure(String orderId, String eventId, String category, boolean recorded)
            implements ProcessingOutcome {
    }
}
