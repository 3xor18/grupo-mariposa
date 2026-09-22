package com.grupomariposa.orders.domain.policy;

import com.grupomariposa.orders.domain.model.ClientProfile;
import com.grupomariposa.orders.domain.model.ClientSegment;
import com.grupomariposa.orders.domain.model.DiscountRule;
import com.grupomariposa.orders.domain.model.Rate;
import java.util.Objects;

public final class WholesaleVolumeDiscountPolicy implements DiscountPolicy {

    private final DiscountRule rule;

    public WholesaleVolumeDiscountPolicy(final DiscountRule rule) {
        this.rule = Objects.requireNonNull(rule, "rule");
    }

    @Override
    public Rate rateFor(final ClientProfile client, final int quantity) {
        final boolean wholesale = client.segment() == ClientSegment.WHOLESALE;
        return wholesale && rule.appliesTo(quantity) ? rule.rate() : Rate.ZERO;
    }
}
