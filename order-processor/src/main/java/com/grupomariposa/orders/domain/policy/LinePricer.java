package com.grupomariposa.orders.domain.policy;

import com.grupomariposa.orders.domain.model.ClientProfile;
import com.grupomariposa.orders.domain.model.LineAmounts;
import com.grupomariposa.orders.domain.model.MarketCode;
import com.grupomariposa.orders.domain.model.Money;
import com.grupomariposa.orders.domain.model.OrderLine;
import com.grupomariposa.orders.domain.model.ProductProfile;
import com.grupomariposa.orders.domain.model.Rate;
import com.grupomariposa.orders.domain.model.RequestedItem;
import java.util.Objects;

public final class LinePricer {

    private final TaxPolicy taxPolicy;
    private final DiscountPolicy discountPolicy;

    public LinePricer(final TaxPolicy taxPolicy, final DiscountPolicy discountPolicy) {
        this.taxPolicy = Objects.requireNonNull(taxPolicy, "taxPolicy");
        this.discountPolicy = Objects.requireNonNull(discountPolicy, "discountPolicy");
    }

    public OrderLine price(final MarketCode market, final int fractionDigits,
                           final ClientProfile client, final RequestedItem item,
                           final ProductProfile product) {
        final Money gross = Money.ofUnits(item.unitPrice(), item.quantity(), fractionDigits);
        final Rate discountRate = discountPolicy.rateFor(client, item.quantity());
        final Money discount = gross.times(discountRate);
        final Money net = gross.minus(discount);
        final Rate taxRate = taxPolicy.rateFor(market, client, product.taxCategory());
        final Money tax = net.times(taxRate);
        final LineAmounts amounts =
                new LineAmounts(gross, discountRate, discount, net, taxRate, tax, net.plus(tax));
        return OrderLine.priced(item, product, amounts);
    }
}
