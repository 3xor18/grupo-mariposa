package com.grupomariposa.orders.application.port.out;

public enum ProcessingStage {
    RECEIVED,
    VALIDATED,
    ENRICHED,
    EVALUATED,
    PERSISTED,
    DUPLICATE,
    STALE,
    CONFLICT,
    TECHNICAL_FAILURE,
    SENT_TO_DLT,
    PUBLISHED,
    PUBLICATION_FAILED
}
