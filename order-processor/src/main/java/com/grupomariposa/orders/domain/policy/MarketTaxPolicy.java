package com.grupomariposa.orders.domain.policy;

import com.grupomariposa.orders.domain.model.ClientProfile;
import com.grupomariposa.orders.domain.model.Market;
import com.grupomariposa.orders.domain.model.Rate;
import com.grupomariposa.orders.domain.model.TaxCategory;
import com.grupomariposa.orders.domain.model.TaxRateTable;
import java.util.Objects;

public final class MarketTaxPolicy implements TaxPolicy {

    private final TaxRateTable table;

    public MarketTaxPolicy(final TaxRateTable table) {
        this.table = Objects.requireNonNull(table, "table");
    }

    @Override
    public Rate rateFor(final Market market, final ClientProfile client,
                        final TaxCategory category) {
        if (client.isTaxExempt()) {
            return Rate.ZERO;
        }
        return table.rateFor(market, category);
    }
}
