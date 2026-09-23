package com.grupomariposa.orders.infrastructure.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.grupomariposa.orders.application.port.out.TaxRateRepository;
import com.grupomariposa.orders.application.port.out.TaxRateSource;
import com.grupomariposa.orders.application.service.TaxRateSeedService;
import com.grupomariposa.orders.domain.DomainFixtures;
import com.grupomariposa.orders.domain.Markets;
import com.grupomariposa.orders.domain.model.Rate;
import com.grupomariposa.orders.domain.model.TaxCategory;
import com.grupomariposa.orders.domain.model.TaxRatePeriod;
import com.grupomariposa.orders.domain.model.TaxRateSchedule;
import com.grupomariposa.orders.infrastructure.observability.CauseSanitizer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RefreshingTaxRateSourceTest {

    private static final Instant JAN_2027 = Instant.parse("2027-01-01T00:00:00Z");

    private final TaxRateRepository repository = mock(TaxRateRepository.class);
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final CauseSanitizer sanitizer = mock(CauseSanitizer.class);
    private final TaxRateSchedule fallback = DomainFixtures.schedule();
    private final RefreshingTaxRateSource source = new RefreshingTaxRateSource(repository,
            DomainFixtures.MARKETS, DomainFixtures.SEED_FROM, fallback, sanitizer, registry);

    @Test
    void should_serve_the_configuration_until_the_first_successful_refresh() {
        when(repository.approvedPeriods()).thenThrow(new IllegalStateException("mongo down"));

        source.refresh();

        assertThat(source.approvedSchedule()).isSameAs(fallback);
        assertThat(source.usingFallback()).isTrue();
        assertThat(registry.get(RefreshingTaxRateSource.FALLBACK).gauge().value()).isOne();
        assertThat(registry.counter(RefreshingTaxRateSource.REFRESH_FAILURES).count()).isOne();
    }

    @Test
    void should_swap_in_the_approved_schedule_and_keep_it_on_invalid_data() {
        final List<TaxRatePeriod> approved = new ArrayList<>(fallback.periods());
        approved.add(new TaxRatePeriod(Markets.MX, TaxCategory.STANDARD, Rate.ofPercent(17),
                JAN_2027.plusSeconds(1), null));
        when(repository.approvedPeriods()).thenReturn(changed(), approved);

        source.refresh();
        final TaxRateSchedule loaded = source.approvedSchedule();
        source.refresh();

        assertThat(loaded.ratesAt(Markets.MX, JAN_2027).table()
                .rateFor(Markets.MX, TaxCategory.STANDARD)).isEqualTo(Rate.ofPercent(17));
        assertThat(source.approvedSchedule()).isSameAs(loaded);
        assertThat(registry.get(RefreshingTaxRateSource.FALLBACK).gauge().value()).isZero();
    }

    @Test
    void should_seed_then_refresh_at_startup_even_when_the_seed_fails() {
        final TaxRateSeedService seeder = mock(TaxRateSeedService.class);
        final TaxRateSource refreshed = mock(TaxRateSource.class);
        final Runnable prepare = mock(Runnable.class);
        doThrow(new IllegalStateException("mongo down")).when(seeder)
                .seed(any(), any(), any());

        new TaxRateBootstrap(seeder, refreshed, DomainFixtures.TAX_RATES, DomainFixtures.MARKETS,
                DomainFixtures.SEED_FROM, sanitizer, prepare).afterSingletonsInstantiated();

        verify(prepare).run();
        verify(refreshed).refresh();
        assertThat(TaxRateSeedService.scheduleOf(DomainFixtures.TAX_RATES,
                DomainFixtures.MARKETS.supportedMarkets(), DomainFixtures.SEED_FROM).periods())
                .hasSameSizeAs(fallback.periods());
    }

    private List<TaxRatePeriod> changed() {
        final List<TaxRatePeriod> periods = new ArrayList<>();
        for (final TaxRatePeriod period : fallback.periods()) {
            if (period.market().equals(Markets.MX) && period.category() == TaxCategory.STANDARD) {
                periods.add(period.closedAt(JAN_2027));
                periods.add(new TaxRatePeriod(Markets.MX, TaxCategory.STANDARD,
                        Rate.ofPercent(17), JAN_2027, null));
            } else {
                periods.add(period);
            }
        }
        return periods;
    }
}
