package com.grupomariposa.orders.infrastructure.pricing;

import com.grupomariposa.orders.application.port.out.TaxRateSource;
import com.grupomariposa.orders.application.service.TaxRateSeedService;
import com.grupomariposa.orders.domain.model.MarketCatalog;
import com.grupomariposa.orders.domain.model.TaxRateTable;
import com.grupomariposa.orders.infrastructure.observability.CauseSanitizer;
import java.time.Instant;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;

public final class TaxRateBootstrap implements SmartInitializingSingleton {

    private static final Logger LOG = LoggerFactory.getLogger(TaxRateBootstrap.class);

    private final TaxRateSeedService seeder;
    private final TaxRateSource source;
    private final TaxRateTable configured;
    private final MarketCatalog markets;
    private final Instant seedFrom;
    private final CauseSanitizer sanitizer;
    private final Runnable prepareStorage;

    public TaxRateBootstrap(final TaxRateSeedService seeder, final TaxRateSource source,
                            final TaxRateTable configured, final MarketCatalog markets,
                            final Instant seedFrom, final CauseSanitizer sanitizer,
                            final Runnable prepareStorage) {
        this.seeder = Objects.requireNonNull(seeder, "seeder");
        this.source = Objects.requireNonNull(source, "source");
        this.configured = Objects.requireNonNull(configured, "configured");
        this.markets = Objects.requireNonNull(markets, "markets");
        this.seedFrom = Objects.requireNonNull(seedFrom, "seedFrom");
        this.sanitizer = Objects.requireNonNull(sanitizer, "sanitizer");
        this.prepareStorage = Objects.requireNonNull(prepareStorage, "prepareStorage");
    }

    @Override
    public void afterSingletonsInstantiated() {
        try {
            prepareStorage.run();
            final int seeded = seeder.seed(configured, markets.supportedMarkets(), seedFrom);
            LOG.info("Tax rates seeded from configuration: {} new periods", seeded);
        } catch (RuntimeException failure) {
            LOG.warn("Tax rate seed failed, using configuration rates: {}",
                    sanitizer.describe(failure));
        }
        source.refresh();
    }
}
