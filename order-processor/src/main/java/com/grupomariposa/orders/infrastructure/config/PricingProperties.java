package com.grupomariposa.orders.infrastructure.config;

import com.grupomariposa.orders.domain.model.Currency;
import com.grupomariposa.orders.domain.model.DiscountRule;
import com.grupomariposa.orders.domain.model.Market;
import com.grupomariposa.orders.domain.model.MarketCurrencies;
import com.grupomariposa.orders.domain.model.Rate;
import com.grupomariposa.orders.domain.model.TaxCategory;
import com.grupomariposa.orders.domain.model.TaxRateTable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "pricing")
public record PricingProperties(
        @NotEmpty Map<Market, @NotEmpty Map<TaxCategory, @NotNull @DecimalMin(DECIMAL_ZERO)
                @DecimalMax(DECIMAL_ONE) BigDecimal>> taxRates,
        @Valid @NotNull WholesaleDiscount wholesaleDiscount,
        @NotEmpty Map<Market, @NotNull Currency> marketCurrencies) {

    private static final String DECIMAL_ZERO = "0.0";
    private static final String DECIMAL_ONE = "1.0";

    public record WholesaleDiscount(
            @NotNull @DecimalMin(DECIMAL_ZERO) @DecimalMax(DECIMAL_ONE) BigDecimal rate,
            @Min(1) int minQuantity) {
    }

    @AssertTrue(message = "every supported market needs a rate for every tax category")
    public boolean isTaxTableComplete() {
        return taxRates != null && marketCurrencies != null
                && taxRates.keySet().containsAll(marketCurrencies.keySet())
                && taxRates.values().stream().allMatch(categories -> categories != null
                && categories.keySet().containsAll(EnumSet.allOf(TaxCategory.class)));
    }

    public TaxRateTable taxRateTable() {
        final Map<Market, Map<TaxCategory, Rate>> table = new EnumMap<>(Market.class);
        taxRates.forEach((market, categories) -> {
            final Map<TaxCategory, Rate> rates = new EnumMap<>(TaxCategory.class);
            categories.forEach((category, rate) -> rates.put(category, new Rate(rate)));
            table.put(market, rates);
        });
        return new TaxRateTable(table);
    }

    public DiscountRule discountRule() {
        return new DiscountRule(new Rate(wholesaleDiscount.rate()),
                wholesaleDiscount.minQuantity());
    }

    public MarketCurrencies markets() {
        return new MarketCurrencies(marketCurrencies);
    }
}
