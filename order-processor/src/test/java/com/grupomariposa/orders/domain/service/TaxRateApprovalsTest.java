package com.grupomariposa.orders.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.grupomariposa.orders.domain.DomainFixtures;
import com.grupomariposa.orders.domain.Markets;
import com.grupomariposa.orders.domain.model.Rate;
import com.grupomariposa.orders.domain.model.TaxCategory;
import com.grupomariposa.orders.domain.model.TaxRatePeriod;
import com.grupomariposa.orders.domain.model.TaxRateProposal;
import com.grupomariposa.orders.domain.model.TaxRateReview;
import com.grupomariposa.orders.domain.model.TaxRateRule;
import com.grupomariposa.orders.domain.model.TaxRateRuleViolation;
import com.grupomariposa.orders.domain.model.TaxRateStatus;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class TaxRateApprovalsTest {

    private static final Instant SEED = DomainFixtures.SEED_FROM;
    private static final Instant NOW = Instant.parse("2026-09-23T10:00:00Z");
    private static final Instant JAN_2027 = Instant.parse("2027-01-01T00:00:00Z");
    private static final Instant JUL_2027 = Instant.parse("2027-07-01T00:00:00Z");
    private static final TaxRateProposal SEEDED = TaxRateProposal.preApproved("seed",
            period(16, SEED, null), "system-seed", SEED, "seed");

    private final TaxRateApprovals approvals = new TaxRateApprovals();

    @Test
    void should_approve_and_close_the_previous_open_period() {
        final TaxRateProposal proposal = proposed(period(17, JAN_2027, null));

        final List<TaxRateProposal> changes =
                approvals.approve(proposal, List.of(SEEDED), "bob", NOW);

        assertThat(changes).hasSize(2);
        assertThat(changes.getFirst().status()).isEqualTo(TaxRateStatus.APPROVED);
        assertThat(changes.getFirst().review()).isEqualTo(new TaxRateReview("bob", NOW));
        assertThat(changes.get(1).id()).isEqualTo("seed");
        assertThat(changes.get(1).period().validTo()).isEqualTo(JAN_2027);
    }

    @Test
    void should_leave_closed_periods_untouched_when_filling_after_them() {
        final TaxRateProposal closed = SEEDED.withPeriod(period(16, SEED, JAN_2027));

        assertThat(approvals.approve(proposed(period(17, JAN_2027, null)), List.of(closed),
                "bob", NOW)).hasSize(1);
    }

    @Test
    void should_reject_overlaps_and_gaps() {
        final TaxRateProposal later = TaxRateProposal.preApproved("later",
                period(18, JUL_2027, null), "carol", NOW, "later");

        assertRule(() -> approvals.approve(proposed(period(17, JAN_2027, null)),
                List.of(SEEDED.withPeriod(period(16, SEED, JAN_2027)), later), "bob", NOW),
                TaxRateRule.OVERLAP);
        assertRule(() -> approvals.approve(proposed(period(17, SEED, null)), List.of(SEEDED),
                "bob", NOW), TaxRateRule.OVERLAP);
        assertRule(() -> approvals.approve(proposed(period(17, JAN_2027, JUL_2027)),
                List.of(SEEDED), "bob", NOW), TaxRateRule.GAP);
    }

    @Test
    void should_enforce_four_eyes_and_pending_status() {
        assertRule(() -> approvals.approve(proposed(period(17, JAN_2027, null)),
                List.of(SEEDED), "alice", NOW), TaxRateRule.FOUR_EYES_REQUIRED);
        assertRule(() -> SEEDED.approvedBy("bob", NOW), TaxRateRule.NOT_PENDING);
        assertRule(() -> SEEDED.rejectedBy("bob", NOW), TaxRateRule.NOT_PENDING);
        final TaxRateProposal rejected = proposed(period(17, JAN_2027, null))
                .rejectedBy("alice", NOW);
        assertThat(rejected.status()).isEqualTo(TaxRateStatus.REJECTED);
        assertThat(rejected.review().reviewer()).isEqualTo("alice");
    }

    @Test
    void should_require_audit_fields() {
        assertThatNullPointerException().isThrownBy(() -> new TaxRateReview(null, NOW));
        assertThatNullPointerException().isThrownBy(() -> new TaxRateReview("bob", null));
        assertThatNullPointerException().isThrownBy(() -> TaxRateProposal.proposed("id",
                period(16, SEED, null), "alice", NOW, null));
        assertThatNullPointerException().isThrownBy(() -> new TaxRateRuleViolation(null, "x"));
    }

    private static void assertRule(final Runnable action, final TaxRateRule rule) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(TaxRateRuleViolation.class,
                violation -> assertThat(violation.rule()).isEqualTo(rule));
    }

    private static TaxRateProposal proposed(final TaxRatePeriod period) {
        return TaxRateProposal.proposed("new", period, "alice", NOW, "IVA change");
    }

    private static TaxRatePeriod period(final int percent, final Instant from, final Instant to) {
        return new TaxRatePeriod(Markets.MX, TaxCategory.STANDARD, Rate.ofPercent(percent),
                from, to);
    }
}
