package com.grupomariposa.orders.infrastructure.config;

import com.grupomariposa.orders.application.port.out.IdGenerator;
import com.grupomariposa.orders.application.port.out.TaxRateRepository;
import com.grupomariposa.orders.application.port.out.TimeProvider;
import com.grupomariposa.orders.application.service.TaxRateSeedService;
import com.grupomariposa.orders.domain.model.MarketCatalog;
import com.grupomariposa.orders.domain.model.TaxRateTable;
import com.grupomariposa.orders.infrastructure.observability.CauseSanitizer;
import com.grupomariposa.orders.infrastructure.persistence.IndexInitializer;
import com.grupomariposa.orders.infrastructure.persistence.MongoTaxRateRepository;
import com.grupomariposa.orders.infrastructure.persistence.TransactionRunner;
import com.grupomariposa.orders.infrastructure.persistence.mapping.TaxRateDocumentMapper;
import com.grupomariposa.orders.infrastructure.pricing.RefreshingTaxRateSource;
import com.grupomariposa.orders.infrastructure.pricing.TaxRateBootstrap;
import com.grupomariposa.orders.infrastructure.pricing.TaxRateProperties;
import com.grupomariposa.orders.infrastructure.pricing.TaxRateRefreshSchedule;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;

@Configuration(proxyBeanMethods = false)
public class PricingConfiguration {

    @Bean(TaxRateRefreshSchedule.BEAN_NAME)
    public TaxRateRefreshSchedule taxRateRefreshSchedule(final TaxRateProperties properties) {
        return new TaxRateRefreshSchedule(properties.refreshInterval());
    }

    @Bean
    public MongoTaxRateRepository taxRateRepository(final MongoTemplate mongoTemplate,
                                                    final TransactionRunner transactions) {
        return new MongoTaxRateRepository(mongoTemplate, transactions,
                new TaxRateDocumentMapper());
    }

    @Bean
    public RefreshingTaxRateSource taxRateSource(final TaxRateRepository repository,
                                                 final MarketCatalog markets,
                                                 final TaxRateTable taxRateTable,
                                                 final TaxRateProperties properties,
                                                 final CauseSanitizer sanitizer,
                                                 final MeterRegistry registry) {
        return new RefreshingTaxRateSource(repository, markets, properties.seedFrom(),
                TaxRateSeedService.scheduleOf(taxRateTable, markets.supportedMarkets(),
                        properties.seedFrom()), sanitizer, registry);
    }

    @Bean
    public TaxRateSeedService taxRateSeedService(final TaxRateRepository repository,
                                                 final IdGenerator idGenerator,
                                                 final TimeProvider timeProvider) {
        return new TaxRateSeedService(repository, idGenerator, timeProvider);
    }

    @Bean
    public TaxRateBootstrap taxRateBootstrap(final TaxRateSeedService seeder,
                                             final RefreshingTaxRateSource source,
                                             final TaxRateTable taxRateTable,
                                             final MarketCatalog markets,
                                             final TaxRateProperties properties,
                                             final CauseSanitizer sanitizer,
                                             final IndexInitializer indexes) {
        return new TaxRateBootstrap(seeder, source, taxRateTable, markets,
                properties.seedFrom(), sanitizer, indexes::ensureTaxRateIndexes);
    }
}
