package com.grupomariposa.orders.infrastructure.persistence.mapping;

import com.grupomariposa.orders.domain.model.MarketCode;
import com.grupomariposa.orders.domain.model.TaxCategory;
import com.grupomariposa.orders.domain.model.TaxRatePeriod;
import com.grupomariposa.orders.domain.model.TaxRateProposal;
import com.grupomariposa.orders.domain.model.TaxRateReview;
import com.grupomariposa.orders.domain.model.TaxRateStatus;
import com.grupomariposa.orders.infrastructure.persistence.document.TaxRateDocument;

public final class TaxRateDocumentMapper {

    public TaxRateDocument toDocument(final TaxRateProposal proposal) {
        final TaxRatePeriod period = proposal.period();
        final TaxRateReview approval = reviewWhen(proposal, TaxRateStatus.APPROVED);
        final TaxRateReview rejection = reviewWhen(proposal, TaxRateStatus.REJECTED);
        return new TaxRateDocument(proposal.id(), period.market().value(),
                period.category().name(), Decimals.of(period.rate()), period.validFrom(),
                period.validTo(), proposal.status().name(), proposal.proposedBy(),
                proposal.proposedAt(), approval == null ? null : approval.reviewer(),
                approval == null ? null : approval.reviewedAt(),
                rejection == null ? null : rejection.reviewer(),
                rejection == null ? null : rejection.reviewedAt(), proposal.changeReason(),
                proposal.version());
    }

    public TaxRateProposal toDomain(final TaxRateDocument document) {
        final TaxRateStatus status = TaxRateStatus.valueOf(document.status());
        return new TaxRateProposal(document.id(), period(document), status,
                document.proposedBy(), document.proposedAt(), document.changeReason(),
                review(document, status), document.version());
    }

    public TaxRatePeriod period(final TaxRateDocument document) {
        return new TaxRatePeriod(new MarketCode(document.market()),
                TaxCategory.valueOf(document.category()), Decimals.toRate(document.rate()),
                document.validFrom(), document.validTo());
    }

    private static TaxRateReview reviewWhen(final TaxRateProposal proposal,
                                            final TaxRateStatus status) {
        return proposal.status() == status ? proposal.review() : null;
    }

    private static TaxRateReview review(final TaxRateDocument document,
                                        final TaxRateStatus status) {
        return switch (status) {
            case PROPOSED -> null;
            case APPROVED -> new TaxRateReview(document.approvedBy(), document.approvedAt());
            case REJECTED -> new TaxRateReview(document.rejectedBy(), document.rejectedAt());
        };
    }
}
