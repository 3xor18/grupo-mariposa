package com.grupomariposa.orders.domain.service;

import static com.grupomariposa.orders.domain.DomainFixtures.evaluator;
import static com.grupomariposa.orders.domain.DomainFixtures.goldenInput;
import static com.grupomariposa.orders.domain.DomainFixtures.item;
import static com.grupomariposa.orders.domain.DomainFixtures.product;
import static com.grupomariposa.orders.domain.DomainFixtures.retailClient;
import static org.assertj.core.api.Assertions.assertThat;

import com.grupomariposa.orders.domain.DomainFixtures;
import com.grupomariposa.orders.domain.Markets;
import com.grupomariposa.orders.domain.model.Decision;
import com.grupomariposa.orders.domain.model.EvaluationInput;
import com.grupomariposa.orders.domain.model.Lookup;
import com.grupomariposa.orders.domain.model.MarketCode;
import com.grupomariposa.orders.domain.model.Money;
import com.grupomariposa.orders.domain.model.OrderLine;
import com.grupomariposa.orders.domain.model.OrderStatus;
import com.grupomariposa.orders.domain.model.RejectionCode;
import com.grupomariposa.orders.domain.model.ResolvedItem;
import com.grupomariposa.orders.domain.model.TaxCategory;
import com.grupomariposa.orders.domain.model.Totals;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

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
    void should_round_chilean_pesos_to_integers() {
        final Decision decision = evaluator.evaluate(
                DomainFixtures.twoLineInput(Markets.CL, "1990", "2590"));

        assertThat(decision.totals()).isEqualTo(new Totals(Money.of("78840"), Money.of("1433"),
                Money.of("77407"), Money.of("14707"), Money.of("92114")));
        assertThat(decision.lines().getFirst().amounts().taxAmount()).isEqualTo(Money.of("8802"));
        assertThat(decision.lines().getLast().amounts().lineTotal()).isEqualTo(Money.of("36985"));
    }

    @Test
    void should_price_ecuador_in_shared_us_dollars() {
        final Decision decision = evaluator.evaluate(
                DomainFixtures.twoLineInput(Markets.EC, "35.5", "82.0"));

        assertThat(decision.totals()).isEqualTo(new Totals(Money.of("1836.00"),
                Money.of("25.56"), Money.of("1810.44"), Money.of("271.57"),
                Money.of("2082.01")));
    }

    @ParameterizedTest(name = "{0}: 20 x 10 retail at standard rate -> {1}")
    @CsvSource({"MX, 232.00", "CO, 238.00", "PE, 236.00", "CL, 238", "EC, 230.00"})
    void should_price_every_catalog_market_with_its_rate_and_precision(final MarketCode market,
                                                                      final String total) {
        final EvaluationInput input = new EvaluationInput(market, DomainFixtures.digits(market),
                Lookup.found(retailClient(market)), List.of(new ResolvedItem(
                        item("PRD-001", 20, "10"),
                        Lookup.found(product("PRD-001", TaxCategory.STANDARD)))));

        assertThat(evaluator.evaluate(input).totals().grandTotal()).isEqualTo(Money.of(total));
    }

    @Test
    void should_reject_with_zero_totals_and_unpriced_lines() {
        final EvaluationInput input = new EvaluationInput(Markets.MX,
                DomainFixtures.digits(Markets.MX), Lookup.notFound(), List.of(
                new ResolvedItem(item("PRD-001", 24, "35.5"),
                        Lookup.found(product("PRD-001", TaxCategory.STANDARD)))));

        final Decision decision = evaluator.evaluate(input);

        assertThat(decision).isInstanceOfSatisfying(Decision.Rejected.class, rejected -> {
            assertThat(rejected.reason()).isEqualTo(RejectionCode.CLIENT_NOT_FOUND);
            assertThat(rejected.status()).isEqualTo(OrderStatus.REJECTED);
            assertThat(rejected.totals()).isEqualTo(Totals.zero(2));
            assertThat(rejected.lines()).singleElement().satisfies(line -> {
                assertThat(line.amounts()).isNull();
                assertThat(line.name()).isEqualTo("Product PRD-001");
            });
        });
    }
}
