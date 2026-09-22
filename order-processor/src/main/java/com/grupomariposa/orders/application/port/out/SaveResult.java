package com.grupomariposa.orders.application.port.out;

public sealed interface SaveResult permits SaveResult.Saved, SaveResult.DuplicateEvent,
        SaveResult.Superseded {

    record Saved() implements SaveResult {
    }

    record DuplicateEvent() implements SaveResult {
    }

    record Superseded(StoredOrderState current) implements SaveResult {
    }
}
