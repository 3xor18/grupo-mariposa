package com.grupomariposa.orders.domain.service;

import com.grupomariposa.orders.domain.model.TaxRatePeriod;
import com.grupomariposa.orders.domain.model.TaxRateProposal;
import com.grupomariposa.orders.domain.model.TaxRateRule;
import com.grupomariposa.orders.domain.model.TaxRateRuleViolation;
import com.grupomariposa.orders.domain.model.TaxRateSchedule;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class TaxRateApprovals {

    private static final String OVERLAP = "Tax rate %s overlaps an approved period";
    private static final String GAP = "Tax rate %s would leave %s %s without a continuous rate";

    public List<TaxRateProposal> approve(final TaxRateProposal target,
                                         final List<TaxRateProposal> approvedSameKey,
                                         final String approver, final Instant at) {
        final TaxRateProposal approved = target.approvedBy(approver, at);
        final TaxRatePeriod candidate = approved.period();
        final List<TaxRateProposal> changes = new ArrayList<>(List.of(approved));
        final List<TaxRatePeriod> others = new ArrayList<>();
        for (final TaxRateProposal existing : approvedSameKey) {
            final TaxRatePeriod period = existing.period();
            if (period.isOpenEnded() && period.validFrom().isBefore(candidate.validFrom())) {
                final TaxRateProposal closed =
                        existing.withPeriod(period.closedAt(candidate.validFrom()));
                changes.add(closed);
                others.add(closed.period());
            } else {
                others.add(period);
            }
        }
        if (others.stream().anyMatch(candidate::overlaps)) {
            throw new TaxRateRuleViolation(TaxRateRule.OVERLAP, OVERLAP.formatted(target.id()));
        }
        final List<TaxRatePeriod> resulting = new ArrayList<>(others);
        resulting.add(candidate);
        final Instant start = resulting.stream().map(TaxRatePeriod::validFrom)
                .min(Instant::compareTo).orElseThrow();
        if (!TaxRateSchedule.coversContinuously(resulting, start)) {
            throw new TaxRateRuleViolation(TaxRateRule.GAP, GAP.formatted(target.id(),
                    candidate.market(), candidate.category()));
        }
        return List.copyOf(changes);
    }
}
