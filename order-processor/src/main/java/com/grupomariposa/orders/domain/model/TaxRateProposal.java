package com.grupomariposa.orders.domain.model;

import java.time.Instant;
import java.util.Objects;

public record TaxRateProposal(String id, TaxRatePeriod period, TaxRateStatus status,
                              String proposedBy, Instant proposedAt, String changeReason,
                              TaxRateReview review, long version) {

    private static final String NOT_PENDING =
            "Tax rate %s is %s; only PROPOSED rates can be reviewed";
    private static final String FOUR_EYES =
            "Tax rate %s must be approved by someone other than its proposer";

    public TaxRateProposal {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(period, "period");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(proposedBy, "proposedBy");
        Objects.requireNonNull(proposedAt, "proposedAt");
        Objects.requireNonNull(changeReason, "changeReason");
    }

    public static TaxRateProposal proposed(final String id, final TaxRatePeriod period,
                                           final String proposedBy, final Instant proposedAt,
                                           final String changeReason) {
        return new TaxRateProposal(id, period, TaxRateStatus.PROPOSED, proposedBy, proposedAt,
                changeReason, null, 0L);
    }

    public static TaxRateProposal preApproved(final String id, final TaxRatePeriod period,
                                              final String actor, final Instant at,
                                              final String changeReason) {
        return new TaxRateProposal(id, period, TaxRateStatus.APPROVED, actor, at, changeReason,
                new TaxRateReview(actor, at), 0L);
    }

    public TaxRateProposal approvedBy(final String approver, final Instant at) {
        requirePending();
        if (proposedBy.equals(approver)) {
            throw new TaxRateRuleViolation(TaxRateRule.FOUR_EYES_REQUIRED,
                    FOUR_EYES.formatted(id));
        }
        return reviewed(TaxRateStatus.APPROVED, new TaxRateReview(approver, at));
    }

    public TaxRateProposal rejectedBy(final String reviewer, final Instant at) {
        requirePending();
        return reviewed(TaxRateStatus.REJECTED, new TaxRateReview(reviewer, at));
    }

    public TaxRateProposal withPeriod(final TaxRatePeriod changed) {
        return new TaxRateProposal(id, changed, status, proposedBy, proposedAt, changeReason,
                review, version);
    }

    private TaxRateProposal reviewed(final TaxRateStatus outcome, final TaxRateReview decision) {
        return new TaxRateProposal(id, period, outcome, proposedBy, proposedAt, changeReason,
                decision, version);
    }

    private void requirePending() {
        if (status != TaxRateStatus.PROPOSED) {
            throw new TaxRateRuleViolation(TaxRateRule.NOT_PENDING,
                    NOT_PENDING.formatted(id, status));
        }
    }
}
