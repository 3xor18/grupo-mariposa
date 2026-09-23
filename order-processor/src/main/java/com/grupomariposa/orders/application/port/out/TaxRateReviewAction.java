package com.grupomariposa.orders.application.port.out;

import com.grupomariposa.orders.domain.model.TaxRateProposal;
import java.util.List;

@FunctionalInterface
public interface TaxRateReviewAction {

    List<TaxRateProposal> apply(TaxRateProposal target, List<TaxRateProposal> approvedSameKey);
}
