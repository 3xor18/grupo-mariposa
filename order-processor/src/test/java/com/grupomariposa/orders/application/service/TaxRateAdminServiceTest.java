package com.grupomariposa.orders.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.grupomariposa.orders.application.FixedTaxRateSource;
import com.grupomariposa.orders.application.command.TaxRateProposalCommand;
import com.grupomariposa.orders.application.error.InvalidTaxRateException;
import com.grupomariposa.orders.application.port.out.TaxRateRepository;
import com.grupomariposa.orders.application.port.out.TaxRateReviewAction;
import com.grupomariposa.orders.application.query.TaxRateFilter;
import com.grupomariposa.orders.application.validation.ValidationError;
import com.grupomariposa.orders.domain.DomainFixtures;
import com.grupomariposa.orders.domain.Markets;
import com.grupomariposa.orders.domain.model.MarketCode;
import com.grupomariposa.orders.domain.model.Rate;
import com.grupomariposa.orders.domain.model.TaxCategory;
import com.grupomariposa.orders.domain.model.TaxRatePeriod;
import com.grupomariposa.orders.domain.model.TaxRateProposal;
import com.grupomariposa.orders.domain.model.TaxRateStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TaxRateAdminServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-23T10:00:00Z");
    private static final Instant JAN_2027 = Instant.parse("2027-01-01T00:00:00Z");
    private static final TaxRateProposal SEEDED = TaxRateProposal.preApproved("seed",
            new TaxRatePeriod(Markets.MX, TaxCategory.STANDARD, Rate.ofPercent(16),
                    DomainFixtures.SEED_FROM, null), "system-seed", NOW, "seed");

    private final TaxRateRepository repository = mock(TaxRateRepository.class);
    private final FixedTaxRateSource source = new FixedTaxRateSource(DomainFixtures.schedule());
    private final TaxRateAdminService service = service(false);

    @Test
    void should_store_valid_proposals_as_proposed() {
        when(repository.insert(any())).thenAnswer(invocation -> invocation.getArgument(0));

        final TaxRateProposal proposal = service.propose(command(Markets.MX, "0.1700",
                JAN_2027, null, " IVA 2027 ", "alice"));

        assertThat(proposal.id()).isEqualTo("id-1");
        assertThat(proposal.status()).isEqualTo(TaxRateStatus.PROPOSED);
        assertThat(proposal.changeReason()).isEqualTo("IVA 2027");
        assertThat(proposal.proposedAt()).isEqualTo(NOW);
        assertThat(proposal.period().rate()).isEqualTo(new Rate(new BigDecimal("0.1700")));
    }

    @Test
    void should_collect_every_validation_error() {
        assertErrors(new TaxRateProposalCommand(null, null, null, null, null, null, null),
                "market", "category", "rate", "validFrom", "changeReason", "proposedBy");
        assertErrors(command(new MarketCode("AR"), "1.5", NOW, NOW.minusSeconds(1), " ", " "),
                "market", "rate", "validFrom", "validTo", "changeReason", "proposedBy");
        assertErrors(command(Markets.MX, "-0.1", JAN_2027, null, "r", "alice"), "rate");
        assertErrors(command(Markets.MX, "0.16005", JAN_2027, null, "r", "alice"), "rate");
        verifyNoInteractions(repository);
    }

    @Test
    void should_accept_past_valid_from_when_allowed() {
        when(repository.insert(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service(true).propose(command(Markets.MX, "0.17", NOW.minusSeconds(60),
                null, "backfill", "alice")).period().validFrom())
                .isEqualTo(NOW.minusSeconds(60));
    }

    @Test
    void should_approve_through_the_repository_and_refresh_the_schedule() {
        final TaxRateProposal pending = TaxRateProposal.proposed("new", new TaxRatePeriod(
                Markets.MX, TaxCategory.STANDARD, Rate.ofPercent(17), JAN_2027, null),
                "alice", NOW, "IVA 2027");
        final ArgumentCaptor<TaxRateReviewAction> action =
                ArgumentCaptor.forClass(TaxRateReviewAction.class);
        when(repository.review(eq("new"), action.capture())).thenAnswer(invocation ->
                action.getValue().apply(pending, List.of(SEEDED)).getFirst());

        final TaxRateProposal approved = service.approve("new", "bob");

        assertThat(approved.status()).isEqualTo(TaxRateStatus.APPROVED);
        assertThat(approved.review().reviewer()).isEqualTo("bob");
        assertThat(action.getValue().apply(pending, List.of(SEEDED))).hasSize(2);
        assertThat(source.refreshes()).isOne();
    }

    @Test
    void should_reject_through_the_repository_without_refreshing() {
        final TaxRateProposal pending = TaxRateProposal.proposed("new", new TaxRatePeriod(
                Markets.MX, TaxCategory.STANDARD, Rate.ofPercent(17), JAN_2027, null),
                "alice", NOW, "IVA 2027");
        final ArgumentCaptor<TaxRateReviewAction> action =
                ArgumentCaptor.forClass(TaxRateReviewAction.class);
        when(repository.review(eq("new"), action.capture())).thenAnswer(invocation ->
                action.getValue().apply(pending, List.of()).getFirst());

        assertThat(service.reject("new", "alice").status()).isEqualTo(TaxRateStatus.REJECTED);
        assertThat(source.refreshes()).isZero();
    }

    @Test
    void should_list_through_the_repository() {
        final TaxRateFilter filter = new TaxRateFilter(Markets.MX, null, TaxRateStatus.APPROVED);
        when(repository.find(filter)).thenReturn(List.of(SEEDED));

        assertThat(service.list(filter)).containsExactly(SEEDED);
    }

    private void assertErrors(final TaxRateProposalCommand command, final String... fields) {
        assertThatThrownBy(() -> service.propose(command))
                .isInstanceOfSatisfying(InvalidTaxRateException.class, invalid ->
                        assertThat(invalid.errors()).extracting(ValidationError::field)
                                .containsExactly(fields));
    }

    private TaxRateAdminService service(final boolean allowPast) {
        return new TaxRateAdminService(repository, source, () -> "id-1", () -> NOW,
                DomainFixtures.MARKETS, allowPast);
    }

    private static TaxRateProposalCommand command(final MarketCode market, final String rate,
                                                  final Instant validFrom,
                                                  final Instant validTo, final String reason,
                                                  final String proposer) {
        return new TaxRateProposalCommand(market, TaxCategory.STANDARD, new BigDecimal(rate),
                validFrom, validTo, reason, proposer);
    }
}
