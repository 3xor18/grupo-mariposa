package com.grupomariposa.orders.domain.policy;

import static com.grupomariposa.orders.domain.DomainFixtures.client;
import static com.grupomariposa.orders.domain.DomainFixtures.discontinued;
import static com.grupomariposa.orders.domain.DomainFixtures.item;
import static com.grupomariposa.orders.domain.DomainFixtures.product;
import static com.grupomariposa.orders.domain.DomainFixtures.wholesaleClient;
import static org.assertj.core.api.Assertions.assertThat;

import com.grupomariposa.orders.domain.DomainFixtures;
import com.grupomariposa.orders.domain.Markets;
import com.grupomariposa.orders.domain.model.ClientProfile;
import com.grupomariposa.orders.domain.model.ClientSegment;
import com.grupomariposa.orders.domain.model.ClientStatus;
import com.grupomariposa.orders.domain.model.EvaluationInput;
import com.grupomariposa.orders.domain.model.Lookup;
import com.grupomariposa.orders.domain.model.MarketCode;
import com.grupomariposa.orders.domain.model.ProductProfile;
import com.grupomariposa.orders.domain.model.RejectionCode;
import com.grupomariposa.orders.domain.model.ResolvedItem;
import com.grupomariposa.orders.domain.model.TaxCategory;
import com.grupomariposa.orders.domain.model.TaxRegime;
import com.grupomariposa.orders.domain.model.Violation;
import java.util.List;
import org.junit.jupiter.api.Test;

class EligibilityPolicyTest {

    private static final Lookup<ProductProfile> ACTIVE_PRODUCT =
            Lookup.found(product("PRD-1", TaxCategory.STANDARD));

    private final EligibilityPolicy policy = new EligibilityPolicy();

    @Test
    void should_accept_active_client_in_market_with_active_products() {
        assertThat(policy.violations(input(Lookup.found(wholesaleClient(Markets.MX)),
                ACTIVE_PRODUCT))).isEmpty();
    }

    @Test
    void should_compare_markets_by_value() {
        assertThat(policy.violations(input(Lookup.found(wholesaleClient(new MarketCode("MX"))),
                ACTIVE_PRODUCT))).isEmpty();
    }

    @Test
    void should_reject_unknown_client_without_further_client_checks() {
        assertThat(codes(policy.violations(input(Lookup.notFound(), ACTIVE_PRODUCT))))
                .containsExactly(RejectionCode.CLIENT_NOT_FOUND);
    }

    @Test
    void should_reject_blocked_client() {
        assertThat(codes(policy.violations(input(Lookup.found(blocked(Markets.MX)),
                ACTIVE_PRODUCT)))).containsExactly(RejectionCode.CLIENT_NOT_ACTIVE);
    }

    @Test
    void should_reject_client_from_another_market() {
        assertThat(codes(policy.violations(input(Lookup.found(wholesaleClient(Markets.CO)),
                ACTIVE_PRODUCT)))).containsExactly(RejectionCode.CLIENT_MARKET_MISMATCH);
    }

    @Test
    void should_reject_unknown_product_with_its_id() {
        assertThat(policy.violations(input(Lookup.found(wholesaleClient(Markets.MX)),
                Lookup.notFound()))).containsExactly(
                Violation.ofProduct(RejectionCode.PRODUCT_NOT_FOUND, "PRD-1"));
    }

    @Test
    void should_reject_discontinued_product_with_its_id() {
        assertThat(policy.violations(input(Lookup.found(wholesaleClient(Markets.MX)),
                Lookup.found(discontinued("PRD-1"))))).containsExactly(
                Violation.ofProduct(RejectionCode.PRODUCT_NOT_ACTIVE, "PRD-1"));
    }

    @Test
    void should_collect_every_violation_in_evaluation_order() {
        final EvaluationInput input = new EvaluationInput(Markets.MX, 2,
                Lookup.found(blocked(Markets.PE)), List.of(
                resolved("PRD-1", Lookup.found(discontinued("PRD-1"))),
                resolved("PRD-2", ACTIVE_PRODUCT),
                resolved("PRD-3", Lookup.notFound())), DomainFixtures.APPLIED_RATES);

        final List<Violation> violations = policy.violations(input);

        assertThat(codes(violations)).containsExactly(RejectionCode.CLIENT_NOT_ACTIVE,
                RejectionCode.CLIENT_MARKET_MISMATCH, RejectionCode.PRODUCT_NOT_ACTIVE,
                RejectionCode.PRODUCT_NOT_FOUND);
        assertThat(violations).extracting(Violation::productId)
                .containsExactly(null, null, "PRD-1", "PRD-3");
    }

    @Test
    void should_combine_missing_client_and_missing_products() {
        final EvaluationInput input = new EvaluationInput(Markets.CO,
                DomainFixtures.digits(Markets.CO), Lookup.notFound(), List.of(
                resolved("PRD-1", Lookup.notFound()), resolved("PRD-2", Lookup.notFound())),
                DomainFixtures.APPLIED_RATES);

        assertThat(codes(policy.violations(input))).containsExactly(
                RejectionCode.CLIENT_NOT_FOUND, RejectionCode.PRODUCT_NOT_FOUND,
                RejectionCode.PRODUCT_NOT_FOUND);
    }

    private static ClientProfile blocked(final MarketCode market) {
        return client(market, ClientSegment.RETAIL, TaxRegime.GENERAL, ClientStatus.BLOCKED);
    }

    private static EvaluationInput input(final Lookup<ClientProfile> client,
                                         final Lookup<ProductProfile> product) {
        return new EvaluationInput(Markets.MX, DomainFixtures.digits(Markets.MX), client,
                List.of(resolved("PRD-1", product)), DomainFixtures.APPLIED_RATES);
    }

    private static ResolvedItem resolved(final String productId,
                                         final Lookup<ProductProfile> product) {
        return new ResolvedItem(item(productId, 1, "1.00"), product);
    }

    private static List<RejectionCode> codes(final List<Violation> violations) {
        return violations.stream().map(Violation::code).toList();
    }
}
