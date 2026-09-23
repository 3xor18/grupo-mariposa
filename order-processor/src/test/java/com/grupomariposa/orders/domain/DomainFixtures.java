package com.grupomariposa.orders.domain;

import com.grupomariposa.orders.domain.model.AppliedTaxRates;
import com.grupomariposa.orders.domain.model.ClientProfile;
import com.grupomariposa.orders.domain.model.ClientSegment;
import com.grupomariposa.orders.domain.model.ClientStatus;
import com.grupomariposa.orders.domain.model.CurrencyCatalog;
import com.grupomariposa.orders.domain.model.CurrencyCode;
import com.grupomariposa.orders.domain.model.DiscountRule;
import com.grupomariposa.orders.domain.model.EvaluationInput;
import com.grupomariposa.orders.domain.model.Lookup;
import com.grupomariposa.orders.domain.model.MarketCatalog;
import com.grupomariposa.orders.domain.model.MarketCode;
import com.grupomariposa.orders.domain.model.MarketDefinition;
import com.grupomariposa.orders.domain.model.ProductProfile;
import com.grupomariposa.orders.domain.model.ProductStatus;
import com.grupomariposa.orders.domain.model.Rate;
import com.grupomariposa.orders.domain.model.RequestedItem;
import com.grupomariposa.orders.domain.model.ResolvedItem;
import com.grupomariposa.orders.domain.model.TaxCategory;
import com.grupomariposa.orders.domain.model.TaxRatePeriod;
import com.grupomariposa.orders.domain.model.TaxRateSchedule;
import com.grupomariposa.orders.domain.model.TaxRateTable;
import com.grupomariposa.orders.domain.model.TaxRegime;
import com.grupomariposa.orders.domain.policy.EligibilityPolicy;
import com.grupomariposa.orders.domain.policy.LinePricer;
import com.grupomariposa.orders.domain.policy.MarketTaxPolicy;
import com.grupomariposa.orders.domain.policy.WholesaleVolumeDiscountPolicy;
import com.grupomariposa.orders.domain.service.OrderEvaluator;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class DomainFixtures {

    public static final String GOLDEN_CLIENT = "CLI-99821";
    public static final String PRD_001 = "PRD-001";
    public static final String PRD_008 = "PRD-008";
    public static final TaxRateTable TAX_RATES = new TaxRateTable(Map.of(
            Markets.MX, rates(16, 8, 0),
            Markets.CO, rates(19, 5, 0),
            Markets.PE, rates(18, 10, 0),
            Markets.CL, rates(19, 19, 0),
            Markets.EC, rates(15, 5, 0)));
    public static final Instant SEED_FROM = Instant.parse("2000-01-01T00:00:00Z");
    public static final AppliedTaxRates APPLIED_RATES = new AppliedTaxRates(TAX_RATES, SEED_FROM);
    public static final CurrencyCatalog CURRENCIES = new CurrencyCatalog(Map.of(
            Currencies.MXN, 2, Currencies.COP, 2, Currencies.PEN, 2, Currencies.CLP, 0,
            Currencies.USD, 2));
    public static final DiscountRule WHOLESALE_DISCOUNT = new DiscountRule(Rate.ofPercent(3), 20);
    public static final MarketCatalog MARKETS = new MarketCatalog(List.of(
            market(Markets.MX, Currencies.MXN, "es-MX"),
            market(Markets.CO, Currencies.COP, "es-CO"),
            market(Markets.PE, Currencies.PEN, "es-PE"),
            market(Markets.CL, Currencies.CLP, "es-CL"),
            market(Markets.EC, Currencies.USD, "es-EC")), CURRENCIES);

    private DomainFixtures() {
    }

    public static MarketDefinition market(final MarketCode code, final CurrencyCode currency,
                                          final String locale) {
        return new MarketDefinition(code, currency, Locale.forLanguageTag(locale));
    }

    public static int digits(final MarketCode market) {
        return MARKETS.fractionDigitsOf(market);
    }

    public static ClientProfile wholesaleClient(final MarketCode market) {
        return client(market, ClientSegment.WHOLESALE, TaxRegime.GENERAL, ClientStatus.ACTIVE);
    }

    public static ClientProfile retailClient(final MarketCode market) {
        return client(market, ClientSegment.RETAIL, TaxRegime.GENERAL, ClientStatus.ACTIVE);
    }

    public static ClientProfile client(final MarketCode market, final ClientSegment segment,
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

    public static EvaluationInput twoLineInput(final MarketCode market, final String firstPrice,
                                               final String secondPrice) {
        return new EvaluationInput(market, digits(market), Lookup.found(wholesaleClient(market)),
                List.of(new ResolvedItem(item(PRD_001, 24, firstPrice),
                                Lookup.found(product(PRD_001, TaxCategory.STANDARD))),
                        new ResolvedItem(item(PRD_008, 12, secondPrice),
                                Lookup.found(product(PRD_008, TaxCategory.STANDARD)))),
                APPLIED_RATES);
    }

    public static EvaluationInput goldenInput() {
        return new EvaluationInput(Markets.MX, digits(Markets.MX),
                Lookup.found(wholesaleClient(Markets.MX)), List.of(
                new ResolvedItem(item(PRD_001, 24, "35.5"),
                        Lookup.found(product(PRD_001, TaxCategory.STANDARD))),
                new ResolvedItem(item(PRD_008, 12, "82.0"),
                        Lookup.found(product(PRD_008, TaxCategory.STANDARD)))), APPLIED_RATES);
    }

    public static Map<TaxCategory, Rate> rates(final int standard, final int reduced,
                                              final int exempt) {
        return Map.of(TaxCategory.STANDARD, Rate.ofPercent(standard),
                TaxCategory.REDUCED, Rate.ofPercent(reduced),
                TaxCategory.EXEMPT, Rate.ofPercent(exempt));
    }

    public static TaxRateSchedule schedule() {
        final List<TaxRatePeriod> periods = new ArrayList<>();
        for (final MarketCode market : MARKETS.supportedMarkets()) {
            for (final TaxCategory category : TaxCategory.values()) {
                periods.add(new TaxRatePeriod(market, category,
                        TAX_RATES.rateFor(market, category), SEED_FROM, null));
            }
        }
        return new TaxRateSchedule(periods);
    }

    public static LinePricer linePricer() {
        return new LinePricer(new MarketTaxPolicy(),
                new WholesaleVolumeDiscountPolicy(WHOLESALE_DISCOUNT));
    }

    public static OrderEvaluator evaluator() {
        return new OrderEvaluator(new EligibilityPolicy(), linePricer());
    }
}
