package com.grupomariposa.orders.domain.model;

import java.util.List;

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

    record Rejected(List<OrderLine> lines, List<Violation> violations) implements Decision {

        public Rejected {
            lines = List.copyOf(lines);
            violations = List.copyOf(violations);
            if (violations.isEmpty()) {
                throw new IllegalArgumentException("A rejection needs at least one violation");
            }
        }

        @Override
        public OrderStatus status() {
            return OrderStatus.REJECTED;
        }

        @Override
        public Totals totals() {
            return Totals.ZERO;
        }

        public RejectionCode reason() {
            return violations.getFirst().code();
        }
    }
}
