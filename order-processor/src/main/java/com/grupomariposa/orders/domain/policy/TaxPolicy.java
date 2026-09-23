package com.grupomariposa.orders.domain.policy;

import com.grupomariposa.orders.domain.model.ClientProfile;
import com.grupomariposa.orders.domain.model.MarketCode;
import com.grupomariposa.orders.domain.model.Rate;
import com.grupomariposa.orders.domain.model.TaxCategory;

public interface TaxPolicy {

    Rate rateFor(MarketCode market, ClientProfile client, TaxCategory category);
}
