package com.grupomariposa.orders.domain.model;

import java.util.List;
import java.util.Objects;

public sealed interface Decision permits Decision.Approved, Decision.Rejected {

    OrderStatus status();

    List<OrderLine> lines();

    Totals totals();

    List<Violation> violations();

    record Approved(List<OrderLine> lines, Totals totals) implements Decision {

        public Approved {
            lines = List.copyOf(lines);
        }

        @Override
        public OrderStatus status() {
            return OrderStatus.APPROVED;
        }

        @Override
        public List<Violation> violations() {
            return List.of();
        }
    }

    record Rejected(List<OrderLine> lines, List<Violation> violations, Totals totals)
            implements Decision {

        private static final String NO_VIOLATIONS = "A rejection needs at least one violation";

        public Rejected {
            lines = List.copyOf(lines);
            violations = List.copyOf(violations);
            Objects.requireNonNull(totals, "totals");
            if (violations.isEmpty()) {
                throw new IllegalArgumentException(NO_VIOLATIONS);
            }
        }

        @Override
        public OrderStatus status() {
            return OrderStatus.REJECTED;
        }

        public RejectionCode reason() {
            return violations.getFirst().code();
        }
    }
}
