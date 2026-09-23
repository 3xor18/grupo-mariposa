package com.grupomariposa.orders.infrastructure.pricing;

import com.grupomariposa.orders.application.port.out.TaxRateRepository;
import com.grupomariposa.orders.application.port.out.TaxRateSource;
import com.grupomariposa.orders.domain.model.MarketCatalog;
import com.grupomariposa.orders.domain.model.TaxRateSchedule;
import com.grupomariposa.orders.infrastructure.observability.CauseSanitizer;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

public final class RefreshingTaxRateSource implements TaxRateSource {

    public static final String REFRESH_FAILURES = "orders.tax_rates.refresh.failures";
    public static final String FALLBACK = "orders.tax_rates.fallback";
    private static final double ACTIVE = 1.0;
    private static final double INACTIVE = 0.0;
    private static final Logger LOG = LoggerFactory.getLogger(RefreshingTaxRateSource.class);

    private final TaxRateRepository repository;
    private final MarketCatalog markets;
    private final Instant coverageFrom;
    private final CauseSanitizer sanitizer;
    private final Counter failures;
    private final AtomicReference<TaxRateSchedule> current;
    private final AtomicBoolean usingFallback = new AtomicBoolean(true);

    public RefreshingTaxRateSource(final TaxRateRepository repository,
                                   final MarketCatalog markets, final Instant coverageFrom,
                                   final TaxRateSchedule fallback,
                                   final CauseSanitizer sanitizer,
                                   final MeterRegistry registry) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.markets = Objects.requireNonNull(markets, "markets");
        this.coverageFrom = Objects.requireNonNull(coverageFrom, "coverageFrom");
        this.sanitizer = Objects.requireNonNull(sanitizer, "sanitizer");
        this.current = new AtomicReference<>(Objects.requireNonNull(fallback, "fallback"));
        this.failures = Counter.builder(REFRESH_FAILURES)
                .description("Tax rate refreshes that kept the previous schedule")
                .register(registry);
        Gauge.builder(FALLBACK, usingFallback, fallbackOn -> fallbackOn.get() ? ACTIVE : INACTIVE)
                .description("1 while orders are priced with the configuration fallback")
                .register(registry);
    }

    @Override
    public TaxRateSchedule approvedSchedule() {
        return current.get();
    }

    @Override
    @Scheduled(fixedDelayString = "#{@taxRateRefreshSchedule.intervalMillis()}",
            initialDelayString = "#{@taxRateRefreshSchedule.intervalMillis()}")
    public void refresh() {
        try {
            final TaxRateSchedule loaded = new TaxRateSchedule(repository.approvedPeriods());
            loaded.requireCoverage(markets.supportedMarkets(), coverageFrom);
            current.set(loaded);
            usingFallback.set(false);
        } catch (RuntimeException failure) {
            failures.increment();
            LOG.warn("Tax rate refresh failed, keeping the {} schedule: {}",
                    usingFallback.get() ? "configuration" : "previous",
                    sanitizer.describe(failure));
        }
    }

    public boolean usingFallback() {
        return usingFallback.get();
    }
}
