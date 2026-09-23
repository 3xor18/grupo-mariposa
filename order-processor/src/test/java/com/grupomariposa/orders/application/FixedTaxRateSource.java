package com.grupomariposa.orders.application;

import com.grupomariposa.orders.application.port.out.TaxRateSource;
import com.grupomariposa.orders.domain.model.TaxRateSchedule;
import java.util.concurrent.atomic.AtomicInteger;

public final class FixedTaxRateSource implements TaxRateSource {

    private final TaxRateSchedule schedule;
    private final AtomicInteger refreshes = new AtomicInteger();

    public FixedTaxRateSource(final TaxRateSchedule schedule) {
        this.schedule = schedule;
    }

    @Override
    public TaxRateSchedule approvedSchedule() {
        return schedule;
    }

    @Override
    public void refresh() {
        refreshes.incrementAndGet();
    }

    public int refreshes() {
        return refreshes.get();
    }
}
