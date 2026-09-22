package com.grupomariposa.orders.domain.policy;

import com.grupomariposa.orders.domain.model.ClientProfile;
import com.grupomariposa.orders.domain.model.ClientSegment;
import com.grupomariposa.orders.domain.model.Rate;

public final class WholesaleVolumeDiscountPolicy implements DiscountPolicy {

    public static final int MINIMUM_QUANTITY = 20;
    public static final Rate DISCOUNT_RATE = Rate.ofPercent(3);

    @Override
    public Rate rateFor(final ClientProfile client, final int quantity) {
        final boolean wholesale = client.segment() == ClientSegment.WHOLESALE;
        return wholesale && quantity >= MINIMUM_QUANTITY ? DISCOUNT_RATE : Rate.ZERO;
    }
}
