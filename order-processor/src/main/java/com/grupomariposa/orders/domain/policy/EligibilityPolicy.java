package com.grupomariposa.orders.domain.policy;

import com.grupomariposa.orders.domain.model.ClientProfile;
import com.grupomariposa.orders.domain.model.EvaluationInput;
import com.grupomariposa.orders.domain.model.Lookup;
import com.grupomariposa.orders.domain.model.MarketCode;
import com.grupomariposa.orders.domain.model.ProductProfile;
import com.grupomariposa.orders.domain.model.RejectionCode;
import com.grupomariposa.orders.domain.model.ResolvedItem;
import com.grupomariposa.orders.domain.model.Violation;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public final class EligibilityPolicy {

    public List<Violation> violations(final EvaluationInput input) {
        final List<Violation> violations = new ArrayList<>(clientViolations(input));
        input.items().stream().flatMap(EligibilityPolicy::productViolations)
                .forEach(violations::add);
        return List.copyOf(violations);
    }

    private static List<Violation> clientViolations(final EvaluationInput input) {
        return switch (input.client()) {
            case Lookup.NotFound<ClientProfile> ignored ->
                    List.of(Violation.of(RejectionCode.CLIENT_NOT_FOUND));
            case Lookup.Found<ClientProfile> found -> foundClientViolations(found.resource(),
                    input.market());
        };
    }

    private static List<Violation> foundClientViolations(final ClientProfile client,
                                                         final MarketCode market) {
        final List<Violation> violations = new ArrayList<>();
        if (!client.isActive()) {
            violations.add(Violation.of(RejectionCode.CLIENT_NOT_ACTIVE));
        }
        if (!client.market().equals(market)) {
            violations.add(Violation.of(RejectionCode.CLIENT_MARKET_MISMATCH));
        }
        return violations;
    }

    private static Stream<Violation> productViolations(final ResolvedItem resolved) {
        final String productId = resolved.item().productId();
        return switch (resolved.product()) {
            case Lookup.NotFound<ProductProfile> ignored ->
                    Stream.of(Violation.ofProduct(RejectionCode.PRODUCT_NOT_FOUND, productId));
            case Lookup.Found<ProductProfile> found -> found.resource().isActive()
                    ? Stream.empty()
                    : Stream.of(Violation.ofProduct(RejectionCode.PRODUCT_NOT_ACTIVE, productId));
        };
    }
}
