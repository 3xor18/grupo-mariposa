package com.grupomariposa.orders.domain.policy;

import static com.grupomariposa.orders.domain.DomainFixtures.client;
import static com.grupomariposa.orders.domain.DomainFixtures.item;
import static com.grupomariposa.orders.domain.DomainFixtures.linePricer;
import static com.grupomariposa.orders.domain.DomainFixtures.product;
import static com.grupomariposa.orders.domain.DomainFixtures.retailClient;
import static com.grupomariposa.orders.domain.DomainFixtures.wholesaleClient;
import static org.assertj.core.api.Assertions.assertThat;

import com.grupomariposa.orders.domain.model.ClientSegment;
import com.grupomariposa.orders.domain.model.ClientStatus;
import com.grupomariposa.orders.domain.model.LineAmounts;
import com.grupomariposa.orders.domain.model.Market;
import com.grupomariposa.orders.domain.model.Money;
import com.grupomariposa.orders.domain.model.OrderLine;
import com.grupomariposa.orders.domain.model.Rate;
import com.grupomariposa.orders.domain.model.TaxCategory;
import com.grupomariposa.orders.domain.model.TaxRegime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class LinePricerTest {

    private final LinePricer pricer = linePricer();

    @Test
    void should_price_discounted_wholesale_line_of_golden_example() {
        final OrderLine line = pricer.price(Market.MX, wholesaleClient(Market.MX),
                item("PRD-001", 24, "35.5"), product("PRD-001", TaxCategory.STANDARD));

        assertThat(line.amounts()).isEqualTo(new LineAmounts(Money.of("852.00"),
                Rate.ofPercent(3), Money.of("25.56"), Money.of("826.44"), Rate.ofPercent(16),
                Money.of("132.23"), Money.of("958.67")));
        assertThat(line.name()).isEqualTo("Product PRD-001");
        assertThat(line.taxCategory()).isEqualTo(TaxCategory.STANDARD);
    }

    @Test
    void should_price_undiscounted_line_of_golden_example() {
        final OrderLine line = pricer.price(Market.MX, wholesaleClient(Market.MX),
                item("PRD-008", 12, "82.0"), product("PRD-008", TaxCategory.STANDARD));

        assertThat(line.amounts().discount()).isEqualTo(Money.ZERO);
        assertThat(line.amounts().taxAmount()).isEqualTo(Money.of("157.44"));
        assertThat(line.amounts().lineTotal()).isEqualTo(Money.of("1141.44"));
    }

    @ParameterizedTest(name = "{0} x {1} -> gross {2} discount {3} net {4} tax {5} total {6}")
    @CsvSource({
        "20, 0.335, 6.70, 0.20, 6.50, 1.04, 7.54",
        "21, 1.005, 21.11, 0.63, 20.48, 3.28, 23.76",
        "25, 0.99, 24.75, 0.74, 24.01, 3.84, 27.85",
        "20, 0.00, 0.00, 0.00, 0.00, 0.00, 0.00"
    })
    void should_round_half_up_at_every_step(final int quantity, final String unitPrice,
                                            final String gross, final String discount,
                                            final String net, final String tax,
                                            final String total) {
        final LineAmounts amounts = pricer.price(Market.MX, wholesaleClient(Market.MX),
                item("PRD-X", quantity, unitPrice), product("PRD-X", TaxCategory.STANDARD))
                .amounts();

        assertThat(amounts.grossSubtotal()).isEqualTo(Money.of(gross));
        assertThat(amounts.discount()).isEqualTo(Money.of(discount));
        assertThat(amounts.netSubtotal()).isEqualTo(Money.of(net));
        assertThat(amounts.taxAmount()).isEqualTo(Money.of(tax));
        assertThat(amounts.lineTotal()).isEqualTo(Money.of(total));
    }

    @Test
    void should_not_discount_retail_and_use_reduced_rate() {
        final LineAmounts amounts = pricer.price(Market.PE, retailClient(Market.PE),
                item("PRD-010", 30, "4.25"), product("PRD-010", TaxCategory.REDUCED)).amounts();

        assertThat(amounts.discountRate().value()).isZero();
        assertThat(amounts.netSubtotal()).isEqualTo(Money.of("127.50"));
        assertThat(amounts.taxAmount()).isEqualTo(Money.of("12.75"));
        assertThat(amounts.lineTotal()).isEqualTo(Money.of("140.25"));
    }

    @Test
    void should_not_tax_exempt_client_even_with_standard_product() {
        final LineAmounts amounts = pricer.price(Market.CO,
                client(Market.CO, ClientSegment.WHOLESALE, TaxRegime.EXEMPT, ClientStatus.ACTIVE),
                item("PRD-006", 20, "10.00"), product("PRD-006", TaxCategory.STANDARD)).amounts();

        assertThat(amounts.taxRate().value()).isZero();
        assertThat(amounts.discount()).isEqualTo(Money.of("6.00"));
        assertThat(amounts.lineTotal()).isEqualTo(Money.of("194.00"));
    }
}
