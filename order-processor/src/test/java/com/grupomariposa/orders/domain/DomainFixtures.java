package com.grupomariposa.orders.domain;

import com.grupomariposa.orders.domain.model.ClientProfile;
import com.grupomariposa.orders.domain.model.ClientSegment;
import com.grupomariposa.orders.domain.model.ClientStatus;
import com.grupomariposa.orders.domain.model.Currency;
import com.grupomariposa.orders.domain.model.DiscountRule;
import com.grupomariposa.orders.domain.model.EvaluationInput;
import com.grupomariposa.orders.domain.model.Lookup;
import com.grupomariposa.orders.domain.model.Market;
import com.grupomariposa.orders.domain.model.MarketCurrencies;
import com.grupomariposa.orders.domain.model.ProductProfile;
import com.grupomariposa.orders.domain.model.ProductStatus;
import com.grupomariposa.orders.domain.model.Rate;
import com.grupomariposa.orders.domain.model.RequestedItem;
import com.grupomariposa.orders.domain.model.ResolvedItem;
import com.grupomariposa.orders.domain.model.TaxCategory;
import com.grupomariposa.orders.domain.model.TaxRateTable;
import com.grupomariposa.orders.domain.model.TaxRegime;
import com.grupomariposa.orders.domain.policy.EligibilityPolicy;
import com.grupomariposa.orders.domain.policy.LinePricer;
import com.grupomariposa.orders.domain.policy.MarketTaxPolicy;
import com.grupomariposa.orders.domain.policy.WholesaleVolumeDiscountPolicy;
import com.grupomariposa.orders.domain.service.OrderEvaluator;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public final class DomainFixtures {

    public static final String GOLDEN_CLIENT = "CLI-99821";
    public static final String PRD_001 = "PRD-001";
    public static final String PRD_008 = "PRD-008";
    public static final TaxRateTable TAX_RATES = new TaxRateTable(Map.of(
            Market.MX, rates(16, 8, 0),
            Market.CO, rates(19, 5, 0),
            Market.PE, rates(18, 10, 0)));
    public static final DiscountRule WHOLESALE_DISCOUNT = new DiscountRule(Rate.ofPercent(3), 20);
    public static final MarketCurrencies MARKETS = new MarketCurrencies(Map.of(
            Market.MX, Currency.MXN, Market.CO, Currency.COP, Market.PE, Currency.PEN));

    private DomainFixtures() {
    }

    public static ClientProfile wholesaleClient(final Market market) {
        return client(market, ClientSegment.WHOLESALE, TaxRegime.GENERAL, ClientStatus.ACTIVE);
    }

    public static ClientProfile retailClient(final Market market) {
        return client(market, ClientSegment.RETAIL, TaxRegime.GENERAL, ClientStatus.ACTIVE);
    }

    public static ClientProfile client(final Market market, final ClientSegment segment,
                                       final TaxRegime regime, final ClientStatus status) {
        return new ClientProfile(GOLDEN_CLIENT, "Distribuidora Central", status, segment, regime,
                market);
    }

    public static ProductProfile product(final String productId, final TaxCategory category) {
        return new ProductProfile(productId, "Product " + productId, "SKU-" + productId,
                ProductStatus.ACTIVE, category);
    }

    public static ProductProfile discontinued(final String productId) {
        return new ProductProfile(productId, "Old " + productId, "SKU-" + productId,
                ProductStatus.DISCONTINUED, TaxCategory.STANDARD);
    }

    public static RequestedItem item(final String productId, final int quantity,
                                     final String unitPrice) {
        return new RequestedItem(productId, quantity, new BigDecimal(unitPrice));
    }

    public static EvaluationInput goldenInput() {
        return new EvaluationInput(Market.MX, Lookup.found(wholesaleClient(Market.MX)), List.of(
                new ResolvedItem(item(PRD_001, 24, "35.5"),
                        Lookup.found(product(PRD_001, TaxCategory.STANDARD))),
                new ResolvedItem(item(PRD_008, 12, "82.0"),
                        Lookup.found(product(PRD_008, TaxCategory.STANDARD)))));
    }

    public static Map<TaxCategory, Rate> rates(final int standard, final int reduced,
                                              final int exempt) {
        return Map.of(TaxCategory.STANDARD, Rate.ofPercent(standard),
                TaxCategory.REDUCED, Rate.ofPercent(reduced),
                TaxCategory.EXEMPT, Rate.ofPercent(exempt));
    }

    public static LinePricer linePricer() {
        return new LinePricer(new MarketTaxPolicy(TAX_RATES),
                new WholesaleVolumeDiscountPolicy(WHOLESALE_DISCOUNT));
    }

    public static OrderEvaluator evaluator() {
        return new OrderEvaluator(new EligibilityPolicy(), linePricer());
    }
}
