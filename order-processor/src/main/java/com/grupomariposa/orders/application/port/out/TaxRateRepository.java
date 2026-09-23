package com.grupomariposa.orders.application.port.out;

import com.grupomariposa.orders.application.query.TaxRateFilter;
import com.grupomariposa.orders.domain.model.MarketCode;
import com.grupomariposa.orders.domain.model.TaxCategory;
import com.grupomariposa.orders.domain.model.TaxRatePeriod;
import com.grupomariposa.orders.domain.model.TaxRateProposal;
import java.util.List;

public interface TaxRateRepository {

    List<TaxRateProposal> find(TaxRateFilter filter);

    TaxRateProposal insert(TaxRateProposal proposal);

    boolean hasApproved(MarketCode market, TaxCategory category);

    boolean insertSeed(TaxRateProposal approved);

    TaxRateProposal review(String id, TaxRateReviewAction action);

    List<TaxRatePeriod> approvedPeriods();
}
