package com.grupomariposa.orders.domain.policy;

import com.grupomariposa.orders.domain.model.ClientProfile;
import com.grupomariposa.orders.domain.model.Market;
import com.grupomariposa.orders.domain.model.Rate;
import com.grupomariposa.orders.domain.model.TaxCategory;

public final class MarketTaxPolicy implements TaxPolicy {

    @Override
    public Rate rateFor(final Market market, final ClientProfile client,
                        final TaxCategory category) {
        if (client.isTaxExempt()) {
            return Rate.ZERO;
        }
        return market.taxRateFor(category);
    }
}
