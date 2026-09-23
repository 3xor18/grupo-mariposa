package com.grupomariposa.orders.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.grupomariposa.orders.domain.DomainFixtures;
import com.grupomariposa.orders.domain.Markets;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TaxRateScheduleTest {

    private static final Instant SEED = DomainFixtures.SEED_FROM;
    private static final Instant JAN_2027 = Instant.parse("2027-01-01T00:00:00Z");
    private static final Instant JUL_2027 = Instant.parse("2027-07-01T00:00:00Z");

    @Test
    void should_validate_period_invariants() {
        assertThatIllegalArgumentException().isThrownBy(() -> period(TaxCategory.STANDARD,
                new Rate(new BigDecimal("1.01")), SEED, null));
        assertThatIllegalArgumentException().isThrownBy(() -> period(TaxCategory.STANDARD,
                Rate.ofPercent(16), SEED, SEED));
        assertThatNullPointerException().isThrownBy(() -> period(TaxCategory.STANDARD,
                Rate.ofPercent(16), null, null));
    }

    @Test
    void should_detect_overlaps_only_within_the_same_market_and_category() {
        final TaxRatePeriod open = period(TaxCategory.STANDARD, Rate.ofPercent(16), SEED, null);
        final TaxRatePeriod closed =
                period(TaxCategory.STANDARD, Rate.ofPercent(16), SEED, JAN_2027);
        final TaxRatePeriod later =
                period(TaxCategory.STANDARD, Rate.ofPercent(17), JAN_2027, null);

        assertThat(open.overlaps(later)).isTrue();
        assertThat(later.overlaps(open)).isTrue();
        assertThat(closed.overlaps(later)).isFalse();
        assertThat(later.overlaps(closed)).isFalse();
        assertThat(open.overlaps(period(TaxCategory.REDUCED, Rate.ofPercent(8), SEED, null)))
                .isFalse();
        assertThat(open.overlaps(new TaxRatePeriod(Markets.CO, TaxCategory.STANDARD,
                Rate.ofPercent(19), SEED, null))).isFalse();
        assertThat(closed.covers(JAN_2027)).isFalse();
        assertThat(closed.covers(SEED)).isTrue();
        assertThat(closed.covers(SEED.minusSeconds(1))).isFalse();
        assertThatIllegalArgumentException().isThrownBy(() -> new TaxRateSchedule(
                List.of(open, later)));
        assertThatIllegalArgumentException().isThrownBy(() -> new TaxRateSchedule(List.of()));
    }

    @Test
    void should_resolve_rates_by_instant_with_the_latest_effective_start() {
        final TaxRateSchedule schedule = withStandardChange();

        final AppliedTaxRates before = schedule.ratesAt(Markets.MX, JAN_2027.minusSeconds(1));
        final AppliedTaxRates after = schedule.ratesAt(Markets.MX, JAN_2027);

        assertThat(before.table().rateFor(Markets.MX, TaxCategory.STANDARD))
                .isEqualTo(Rate.ofPercent(16));
        assertThat(before.effectiveFrom()).isEqualTo(SEED);
        assertThat(after.table().rateFor(Markets.MX, TaxCategory.STANDARD))
                .isEqualTo(Rate.ofPercent(17));
        assertThat(after.table().rateFor(Markets.MX, TaxCategory.REDUCED))
                .isEqualTo(Rate.ofPercent(8));
        assertThat(after.effectiveFrom()).isEqualTo(JAN_2027);
        assertThat(schedule.earliestStart()).isEqualTo(SEED);
    }

    @Test
    void should_fail_when_no_period_covers_the_instant() {
        assertThatIllegalArgumentException().isThrownBy(() -> DomainFixtures.schedule()
                .ratesAt(Markets.MX, SEED.minusSeconds(1)));
    }

    @Test
    void should_require_continuous_coverage_for_every_catalog_market() {
        DomainFixtures.schedule().requireCoverage(DomainFixtures.MARKETS.supportedMarkets(),
                SEED);
        withStandardChange().requireCoverage(List.of(Markets.MX), SEED);

        assertThatIllegalArgumentException().isThrownBy(() -> DomainFixtures.schedule()
                .requireCoverage(List.of(Markets.MX), SEED.minusSeconds(1)));
        assertThatIllegalArgumentException().isThrownBy(() -> withGap()
                .requireCoverage(List.of(Markets.MX), SEED));
        assertThatIllegalArgumentException().isThrownBy(() -> closedAtEnd()
                .requireCoverage(List.of(Markets.MX), SEED));
        assertThatIllegalArgumentException().isThrownBy(() -> DomainFixtures.schedule()
                .requireCoverage(List.of(new MarketCode("AR")), SEED));
    }

    private static TaxRateSchedule withStandardChange() {
        return replacingStandard(List.of(
                period(TaxCategory.STANDARD, Rate.ofPercent(16), SEED, JAN_2027),
                period(TaxCategory.STANDARD, Rate.ofPercent(17), JAN_2027, null)));
    }

    private static TaxRateSchedule withGap() {
        return replacingStandard(List.of(
                period(TaxCategory.STANDARD, Rate.ofPercent(16), SEED, JAN_2027),
                period(TaxCategory.STANDARD, Rate.ofPercent(17), JUL_2027, null)));
    }

    private static TaxRateSchedule closedAtEnd() {
        return replacingStandard(List.of(
                period(TaxCategory.STANDARD, Rate.ofPercent(16), SEED, JAN_2027)));
    }

    private static TaxRateSchedule replacingStandard(final List<TaxRatePeriod> standard) {
        final List<TaxRatePeriod> periods = new ArrayList<>(DomainFixtures.schedule().periods()
                .stream().filter(period -> !(period.market().equals(Markets.MX)
                        && period.category() == TaxCategory.STANDARD)).toList());
        periods.addAll(standard);
        return new TaxRateSchedule(periods);
    }

    static TaxRatePeriod period(final TaxCategory category, final Rate rate, final Instant from,
                                final Instant to) {
        return new TaxRatePeriod(Markets.MX, category, rate, from, to);
    }
}
