package com.grupomariposa.orders.infrastructure.config;

import com.grupomariposa.orders.application.port.out.ClientDirectory;
import com.grupomariposa.orders.application.port.out.EventPublisher;
import com.grupomariposa.orders.application.port.out.IdGenerator;
import com.grupomariposa.orders.application.port.out.OrderQueryRepository;
import com.grupomariposa.orders.application.port.out.OrderStore;
import com.grupomariposa.orders.application.port.out.OutboxStore;
import com.grupomariposa.orders.application.port.out.ProcessingObserver;
import com.grupomariposa.orders.application.port.out.ProductCatalog;
import com.grupomariposa.orders.application.port.out.TimeProvider;
import com.grupomariposa.orders.application.service.OrderAssembler;
import com.grupomariposa.orders.application.service.OrderEnricher;
import com.grupomariposa.orders.application.service.OrderQueryService;
import com.grupomariposa.orders.application.service.ProcessOrderService;
import com.grupomariposa.orders.application.service.PublishPendingEventsService;
import com.grupomariposa.orders.application.service.RecordTechnicalFailureService;
import com.grupomariposa.orders.application.service.RelaySettings;
import com.grupomariposa.orders.application.validation.OrderCommandValidator;
import com.grupomariposa.orders.domain.model.DiscountRule;
import com.grupomariposa.orders.domain.model.MarketCurrencies;
import com.grupomariposa.orders.domain.model.TaxRateTable;
import com.grupomariposa.orders.domain.policy.EligibilityPolicy;
import com.grupomariposa.orders.domain.policy.LinePricer;
import com.grupomariposa.orders.domain.policy.MarketTaxPolicy;
import com.grupomariposa.orders.domain.policy.WholesaleVolumeDiscountPolicy;
import com.grupomariposa.orders.domain.service.OrderEvaluator;
import com.grupomariposa.orders.infrastructure.kafka.OutboxRelayProperties;
import com.grupomariposa.orders.infrastructure.system.ManagedVirtualThreadExecutor;
import com.grupomariposa.orders.infrastructure.system.SystemTimeProvider;
import com.grupomariposa.orders.infrastructure.system.UuidV7Generator;
import java.time.Clock;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods = false)
public class ApplicationConfiguration {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public TimeProvider timeProvider(final Clock clock) {
        return new SystemTimeProvider(clock);
    }

    @Bean
    public IdGenerator idGenerator(final Clock clock) {
        return new UuidV7Generator(clock);
    }

    @Bean
    public EnvironmentSecrets environmentSecrets(final Environment environment) {
        return new EnvironmentSecrets(environment);
    }

    @Bean
    public TaxRateTable taxRateTable(final PricingProperties pricing) {
        return pricing.taxRateTable();
    }

    @Bean
    public DiscountRule wholesaleDiscountRule(final PricingProperties pricing) {
        return pricing.discountRule();
    }

    @Bean
    public MarketCurrencies marketCurrencies(final PricingProperties pricing) {
        return pricing.markets();
    }

    @Bean
    public OrderEvaluator orderEvaluator(final TaxRateTable taxRates,
                                         final DiscountRule wholesaleDiscountRule) {
        return new OrderEvaluator(new EligibilityPolicy(), new LinePricer(
                new MarketTaxPolicy(taxRates),
                new WholesaleVolumeDiscountPolicy(wholesaleDiscountRule)));
    }

    @Bean
    public OrderCommandValidator orderCommandValidator(final MarketCurrencies markets,
                                                       final ValidationProperties validation) {
        return new OrderCommandValidator(markets, validation.rules());
    }

    @Bean
    public OrderAssembler orderAssembler(final TimeProvider timeProvider) {
        return new OrderAssembler(timeProvider);
    }

    @Bean(destroyMethod = "close")
    public ManagedVirtualThreadExecutor lookupExecutor() {
        return new ManagedVirtualThreadExecutor();
    }

    @Bean
    public OrderEnricher orderEnricher(final ClientDirectory clientDirectory,
                                       final ProductCatalog productCatalog,
                                       final ManagedVirtualThreadExecutor lookupExecutor,
                                       final ProcessingProperties properties) {
        return new OrderEnricher(clientDirectory, productCatalog, lookupExecutor.executor(),
                properties.maxConcurrentLookups());
    }

    @Bean
    public ProcessOrderService processOrderService(final OrderEnricher enricher,
                                                   final OrderEvaluator evaluator,
                                                   final OrderAssembler assembler,
                                                   final OrderStore store,
                                                   final IdGenerator idGenerator,
                                                   final ProcessingObserver observer) {
        return new ProcessOrderService(enricher, evaluator, assembler, store, idGenerator,
                observer);
    }

    @Bean
    public RecordTechnicalFailureService recordTechnicalFailureService(
            final OrderAssembler assembler, final OrderStore store,
            final ProcessingObserver observer) {
        return new RecordTechnicalFailureService(assembler, store, observer);
    }

    @Bean
    public OrderQueryService orderQueryService(final OrderQueryRepository repository) {
        return new OrderQueryService(repository);
    }

    @Bean
    public PublishPendingEventsService publishPendingEventsService(
            final OutboxStore outboxStore, final EventPublisher publisher,
            final TimeProvider timeProvider, final ProcessingObserver observer,
            final OutboxRelayProperties relay, final ProcessingProperties processing) {
        final String owner = processing.instanceId() == null || processing.instanceId().isBlank()
                ? UUID.randomUUID().toString() : processing.instanceId();
        return new PublishPendingEventsService(outboxStore, publisher, timeProvider, observer,
                new RelaySettings(relay.batchSize(), relay.lease(), relay.sendTimeout(),
                        relay.initialBackoff(), relay.maxBackoff(), owner));
    }
}
