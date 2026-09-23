package com.grupomariposa.orders.domain.policy;

import com.grupomariposa.orders.domain.model.ClientProfile;
import com.grupomariposa.orders.domain.model.Rate;

public interface DiscountPolicy {

    Rate rateFor(ClientProfile client, int quantity);
}
