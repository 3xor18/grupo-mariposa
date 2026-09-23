package com.grupomariposa.orders.domain.policy;

import com.grupomariposa.orders.domain.model.ClientProfile;
import com.grupomariposa.orders.domain.model.MarketCode;
import com.grupomariposa.orders.domain.model.Rate;
import com.grupomariposa.orders.domain.model.TaxCategory;
import com.grupomariposa.orders.domain.model.TaxRateTable;

public final class MarketTaxPolicy implements TaxPolicy {

    @Override
    public Rate rateFor(final TaxRateTable rates, final MarketCode market,
                        final ClientProfile client, final TaxCategory category) {
        if (client.isTaxExempt()) {
            return Rate.ZERO;
        }
        return rates.rateFor(market, category);
    }
}
