package com.grupomariposa.orders.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record Order(
        OrderIdentity identity,
        OrderStatus status,
        Market market,
        Currency currency,
        String channel,
        ClientSnapshot client,
        List<OrderLine> lines,
        Totals totals,
        List<Violation> violations,
        FailureDetails failure,
        OrderTimeline timeline,
        String traceId) {

    public Order {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(market, "market");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(client, "client");
        Objects.requireNonNull(totals, "totals");
        Objects.requireNonNull(timeline, "timeline");
        lines = List.copyOf(lines);
        violations = List.copyOf(violations);
    }

    public String orderId() {
        return identity.orderId();
    }

    public String sourceEventId() {
        return identity.sourceEventId();
    }

    public int eventVersion() {
        return identity.eventVersion();
    }

    public Instant processedAt() {
        return timeline.processedAt();
    }

    public Optional<RejectionCode> reason() {
        return violations.stream().map(Violation::code).findFirst();
    }
}
