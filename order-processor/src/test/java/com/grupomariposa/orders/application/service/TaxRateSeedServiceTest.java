package com.grupomariposa.orders.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.grupomariposa.orders.application.port.out.TaxRateRepository;
import com.grupomariposa.orders.domain.DomainFixtures;
import com.grupomariposa.orders.domain.Markets;
import com.grupomariposa.orders.domain.model.TaxCategory;
import com.grupomariposa.orders.domain.model.TaxRateProposal;
import com.grupomariposa.orders.domain.model.TaxRateStatus;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TaxRateSeedServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-23T10:00:00Z");

    private final TaxRateRepository repository = mock(TaxRateRepository.class);
    private final TaxRateSeedService seeder =
            new TaxRateSeedService(repository, () -> "seed-id", () -> NOW);

    @Test
    void should_seed_only_missing_market_categories_as_approved_by_the_system() {
        when(repository.hasApproved(Markets.MX, TaxCategory.STANDARD)).thenReturn(true);
        when(repository.insertSeed(any())).thenReturn(true, false);

        final int inserted = seeder.seed(DomainFixtures.TAX_RATES, List.of(Markets.MX),
                DomainFixtures.SEED_FROM);

        final ArgumentCaptor<TaxRateProposal> seeded =
                ArgumentCaptor.forClass(TaxRateProposal.class);
        verify(repository, times(2)).insertSeed(seeded.capture());
        assertThat(inserted).isOne();
        assertThat(seeded.getAllValues()).allSatisfy(proposal -> {
            assertThat(proposal.status()).isEqualTo(TaxRateStatus.APPROVED);
            assertThat(proposal.proposedBy()).isEqualTo(TaxRateSeedService.SEED_ACTOR);
            assertThat(proposal.review().reviewer()).isEqualTo(TaxRateSeedService.SEED_ACTOR);
            assertThat(proposal.period().validFrom()).isEqualTo(DomainFixtures.SEED_FROM);
            assertThat(proposal.period().isOpenEnded()).isTrue();
        });
    }

    @Test
    void should_not_seed_when_every_rate_exists() {
        when(repository.hasApproved(any(), any())).thenReturn(true);

        assertThat(seeder.seed(DomainFixtures.TAX_RATES, List.of(Markets.CO),
                DomainFixtures.SEED_FROM)).isZero();
        verify(repository, never()).insertSeed(any());
    }

    @Test
    void should_build_the_configuration_schedule() {
        TaxRateSeedService.scheduleOf(DomainFixtures.TAX_RATES,
                DomainFixtures.MARKETS.supportedMarkets(), DomainFixtures.SEED_FROM)
                .requireCoverage(DomainFixtures.MARKETS.supportedMarkets(),
                        DomainFixtures.SEED_FROM);
    }
}
