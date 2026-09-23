package com.grupomariposa.orders.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.grupomariposa.orders.application.ApplicationFixtures;
import com.grupomariposa.orders.application.FixedTaxRateSource;
import com.grupomariposa.orders.application.command.OrderCommand;
import com.grupomariposa.orders.application.port.out.ClientDirectory;
import com.grupomariposa.orders.application.port.out.ProductCatalog;
import com.grupomariposa.orders.domain.DomainFixtures;
import com.grupomariposa.orders.domain.Markets;
import com.grupomariposa.orders.domain.model.Lookup;
import com.grupomariposa.orders.domain.model.Rate;
import com.grupomariposa.orders.domain.model.TaxCategory;
import com.grupomariposa.orders.domain.model.TaxRatePeriod;
import com.grupomariposa.orders.domain.model.TaxRateSchedule;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class OrderEnricherTaxRatesTest {

    private static final Instant CHANGE = Instant.parse("2026-09-18T15:42:10.500Z");

    private final ClientDirectory clients = mock(ClientDirectory.class);
    private final ProductCatalog products = mock(ProductCatalog.class);

    @ParameterizedTest(name = "occurredAt {0} -> {1}% effective from {2}")
    @CsvSource({
        "2026-09-18T15:42:10Z, 16, 2000-01-01T00:00:00Z",
        ", 17, 2026-09-18T15:42:10.500Z",
        "1990-01-01T00:00:00Z, 16, 2000-01-01T00:00:00Z"
    })
    void should_price_with_the_rates_in_force_when_the_order_occurred(
            final Instant occurredAt, final int standardPercent, final Instant effectiveFrom) {
        when(clients.findClient(anyString())).thenReturn(Lookup.notFound());
        when(products.findProduct(anyString(), any())).thenReturn(Lookup.notFound());
        final OrderEnricher enricher = new OrderEnricher(clients, products, Runnable::run, 1,
                DomainFixtures.CURRENCIES, new FixedTaxRateSource(scheduleWithChange()));

        final var rates = enricher.enrich(command(occurredAt)).taxRates();

        assertThat(rates.table().rateFor(Markets.MX, TaxCategory.STANDARD))
                .isEqualTo(Rate.ofPercent(standardPercent));
        assertThat(rates.effectiveFrom()).isEqualTo(effectiveFrom);
    }

    private static OrderCommand command(final Instant occurredAt) {
        final OrderCommand golden = ApplicationFixtures.goldenCommand();
        return new OrderCommand(golden.eventId(), golden.eventVersion(), golden.orderId(),
                golden.market(), golden.currency(), golden.clientId(), golden.channel(),
                occurredAt, golden.items(), golden.reception());
    }

    private static TaxRateSchedule scheduleWithChange() {
        final List<TaxRatePeriod> periods = new ArrayList<>();
        for (final TaxRatePeriod period : DomainFixtures.schedule().periods()) {
            if (period.market().equals(Markets.MX) && period.category() == TaxCategory.STANDARD) {
                periods.add(period.closedAt(CHANGE));
                periods.add(new TaxRatePeriod(Markets.MX, TaxCategory.STANDARD,
                        Rate.ofPercent(17), CHANGE, null));
            } else {
                periods.add(period);
            }
        }
        return new TaxRateSchedule(periods);
    }
}
