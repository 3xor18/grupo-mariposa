package com.grupomariposa.orders.domain.model;

import static com.grupomariposa.orders.domain.DomainFixtures.item;
import static com.grupomariposa.orders.domain.DomainFixtures.product;
import static com.grupomariposa.orders.domain.DomainFixtures.wholesaleClient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.grupomariposa.orders.domain.Currencies;
import com.grupomariposa.orders.domain.DomainFixtures;
import com.grupomariposa.orders.domain.Markets;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ModelInvariantsTest {

    private static final Instant NOW = Instant.parse("2026-09-18T15:42:10Z");

    @Test
    void should_reject_non_positive_quantity_and_negative_price() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new RequestedItem("PRD-1", 0, BigDecimal.ONE));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new RequestedItem("PRD-1", 1, new BigDecimal("-0.01")));
        assertThat(new RequestedItem("PRD-1", 1, BigDecimal.ZERO).unitPrice()).isZero();
    }

    @Test
    void should_expose_lookup_values() {
        assertThat(Lookup.found("x").value()).contains("x");
        assertThat(Lookup.notFound().value()).isEmpty();
        assertThatNullPointerException().isThrownBy(() -> Lookup.found(null));
    }

    @Test
    void should_map_found_lookups_and_keep_not_found() {
        assertThat(Lookup.found("abc").map(String::length)).isEqualTo(Lookup.found(3));
        assertThat(Lookup.<String>notFound().map(String::length)).isEqualTo(Lookup.notFound());
    }

    @Test
    void should_build_violations_with_catalog_messages() {
        assertThat(Violation.of(RejectionCode.CLIENT_NOT_FOUND))
                .isEqualTo(new Violation(RejectionCode.CLIENT_NOT_FOUND,
                        RejectionCode.CLIENT_NOT_FOUND.message(), null));
        assertThat(Violation.ofProduct(RejectionCode.PRODUCT_NOT_ACTIVE, "PRD-4").productId())
                .isEqualTo("PRD-4");
    }

    @Test
    void should_snapshot_resolved_and_unresolved_clients() {
        final ClientProfile profile = wholesaleClient(Markets.MX);

        assertThat(ClientSnapshot.of("CLI-1", Lookup.found(profile)).market())
                .isEqualTo(Markets.MX);
        assertThat(ClientSnapshot.of("CLI-1", Lookup.notFound()))
                .isEqualTo(ClientSnapshot.unresolved("CLI-1"));
    }

    @Test
    void should_build_unpriced_lines_with_known_product_data() {
        final RequestedItem requested = item("PRD-1", 2, "1.50");

        assertThat(OrderLine.unpriced(requested, Lookup.found(product("PRD-1",
                TaxCategory.REDUCED))).taxCategory()).isEqualTo(TaxCategory.REDUCED);
        assertThat(OrderLine.unpriced(requested, Lookup.notFound()).name()).isNull();
        assertThat(OrderLine.unpriced(requested).amounts()).isNull();
    }

    @Test
    void should_validate_failure_identity_and_rejections() {
        assertThatIllegalArgumentException().isThrownBy(() -> new FailureDetails("X", "y", 0));
        assertThatIllegalArgumentException().isThrownBy(() -> new OrderIdentity("o", "e", 0));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Decision.Rejected(List.of(), List.of(), Totals.zero(2)));
        assertThatIllegalArgumentException().isThrownBy(() -> new EvaluationInput(
                new MarketCode("MX"), -1, Lookup.notFound(), List.of(),
                DomainFixtures.APPLIED_RATES));
    }

    @Test
    void should_derive_reason_from_first_violation_only() {
        assertThat(order(List.of(Violation.of(RejectionCode.CLIENT_NOT_FOUND),
                Violation.of(RejectionCode.CLIENT_NOT_ACTIVE))).reason())
                .contains(RejectionCode.CLIENT_NOT_FOUND);
        assertThat(order(List.of()).reason()).isEmpty();
    }

    @Test
    void should_classify_terminal_statuses() {
        assertThat(OrderStatus.APPROVED.isTerminal()).isTrue();
        assertThat(OrderStatus.REJECTED.isTerminal()).isTrue();
        assertThat(OrderStatus.TECHNICAL_FAILURE.isTerminal()).isFalse();
    }

    @Test
    void should_expose_profile_predicates() {
        final ProductProfile product = product("PRD-1", TaxCategory.EXEMPT);

        assertThat(product.isActive()).isTrue();
        assertThat(wholesaleClient(Markets.PE).isTaxExempt()).isFalse();
    }

    private static Order order(final List<Violation> violations) {
        return new Order(new OrderIdentity("ORD-1", "EVT-1", 2),
                violations.isEmpty() ? OrderStatus.APPROVED : OrderStatus.REJECTED,
                Markets.MX, Currencies.MXN, null, ClientSnapshot.unresolved("CLI-1"),
                List.of(), Totals.zero(2), violations, null, new OrderTimeline(null, NOW, NOW),
                null, null);
    }
}
