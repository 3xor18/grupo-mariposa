package com.grupomariposa.orders.domain.service;

import com.grupomariposa.orders.domain.model.ClientProfile;
import com.grupomariposa.orders.domain.model.Decision;
import com.grupomariposa.orders.domain.model.EvaluationInput;
import com.grupomariposa.orders.domain.model.LineAmounts;
import com.grupomariposa.orders.domain.model.OrderLine;
import com.grupomariposa.orders.domain.model.ProductProfile;
import com.grupomariposa.orders.domain.model.ResolvedItem;
import com.grupomariposa.orders.domain.model.Totals;
import com.grupomariposa.orders.domain.model.Violation;
import com.grupomariposa.orders.domain.policy.EligibilityPolicy;
import com.grupomariposa.orders.domain.policy.LinePricer;
import java.util.List;
import java.util.Objects;

public final class OrderEvaluator {

    private final EligibilityPolicy eligibilityPolicy;
    private final LinePricer linePricer;

    public OrderEvaluator(final EligibilityPolicy eligibilityPolicy, final LinePricer linePricer) {
        this.eligibilityPolicy = Objects.requireNonNull(eligibilityPolicy, "eligibilityPolicy");
        this.linePricer = Objects.requireNonNull(linePricer, "linePricer");
    }

    public Decision evaluate(final EvaluationInput input) {
        final List<Violation> violations = eligibilityPolicy.violations(input);
        if (!violations.isEmpty()) {
            return new Decision.Rejected(unpricedLines(input), violations);
        }
        final ClientProfile client = input.client().value().orElseThrow();
        final List<OrderLine> lines = input.items().stream()
                .map(resolved -> priceLine(input, client, resolved))
                .toList();
        return new Decision.Approved(lines, totalsOf(lines));
    }

    private OrderLine priceLine(final EvaluationInput input, final ClientProfile client,
                                final ResolvedItem resolved) {
        final ProductProfile product = resolved.product().value().orElseThrow();
        return linePricer.price(input.market(), client, resolved.item(), product);
    }

    private static List<OrderLine> unpricedLines(final EvaluationInput input) {
        return input.items().stream()
                .map(resolved -> OrderLine.unpriced(resolved.item(), resolved.product()))
                .toList();
    }

    private static Totals totalsOf(final List<OrderLine> lines) {
        final List<LineAmounts> amounts = lines.stream()
                .map(line -> line.pricing().orElseThrow())
                .toList();
        return Totals.sumOf(amounts);
    }
}
