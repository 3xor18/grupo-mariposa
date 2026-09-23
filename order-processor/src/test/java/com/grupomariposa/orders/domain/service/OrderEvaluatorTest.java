package com.grupomariposa.orders.domain.service;

import static com.grupomariposa.orders.domain.DomainFixtures.evaluator;
import static com.grupomariposa.orders.domain.DomainFixtures.goldenInput;
import static com.grupomariposa.orders.domain.DomainFixtures.item;
import static com.grupomariposa.orders.domain.DomainFixtures.product;
import static org.assertj.core.api.Assertions.assertThat;

import com.grupomariposa.orders.domain.model.Decision;
import com.grupomariposa.orders.domain.model.EvaluationInput;
import com.grupomariposa.orders.domain.model.Lookup;
import com.grupomariposa.orders.domain.model.Market;
import com.grupomariposa.orders.domain.model.Money;
import com.grupomariposa.orders.domain.model.OrderLine;
import com.grupomariposa.orders.domain.model.OrderStatus;
import com.grupomariposa.orders.domain.model.RejectionCode;
import com.grupomariposa.orders.domain.model.ResolvedItem;
import com.grupomariposa.orders.domain.model.TaxCategory;
import com.grupomariposa.orders.domain.model.Totals;
import java.util.List;
import org.junit.jupiter.api.Test;

class OrderEvaluatorTest {

    private final OrderEvaluator evaluator = evaluator();

    @Test
    void should_approve_golden_example_with_exact_totals() {
        final Decision decision = evaluator.evaluate(goldenInput());

        assertThat(decision).isInstanceOf(Decision.Approved.class);
        assertThat(decision.status()).isEqualTo(OrderStatus.APPROVED);
        assertThat(decision.violations()).isEmpty();
        assertThat(decision.totals()).isEqualTo(new Totals(Money.of("1836.00"),
                Money.of("25.56"), Money.of("1810.44"), Money.of("289.67"),
                Money.of("2100.11")));
        assertThat(decision.lines()).extracting(OrderLine::productId)
                .containsExactly("PRD-001", "PRD-008");
    }

    @Test
    void should_reject_with_zero_totals_and_unpriced_lines() {
        final EvaluationInput input = new EvaluationInput(Market.MX, Lookup.notFound(), List.of(
                new ResolvedItem(item("PRD-001", 24, "35.5"),
                        Lookup.found(product("PRD-001", TaxCategory.STANDARD)))));

        final Decision decision = evaluator.evaluate(input);

        assertThat(decision).isInstanceOfSatisfying(Decision.Rejected.class, rejected -> {
            assertThat(rejected.reason()).isEqualTo(RejectionCode.CLIENT_NOT_FOUND);
            assertThat(rejected.status()).isEqualTo(OrderStatus.REJECTED);
            assertThat(rejected.totals()).isEqualTo(Totals.ZERO);
            assertThat(rejected.lines()).singleElement().satisfies(line -> {
                assertThat(line.amounts()).isNull();
                assertThat(line.name()).isEqualTo("Product PRD-001");
            });
        });
    }
}
