package com.grupomariposa.orders.application.port.out;

public enum ProcessingStage {
    RECEIVED(false),
    VALIDATED(false),
    ENRICHED(false),
    EVALUATED(false),
    PERSISTED(true),
    DUPLICATE(true),
    STALE(true),
    CONFLICT(true),
    TECHNICAL_FAILURE(true),
    SENT_TO_DLT(true),
    PUBLISHED(true),
    PUBLICATION_FAILED(true);

    private final boolean terminal;

    ProcessingStage(final boolean terminal) {
        this.terminal = terminal;
    }

    public boolean isTerminal() {
        return terminal;
    }
}
