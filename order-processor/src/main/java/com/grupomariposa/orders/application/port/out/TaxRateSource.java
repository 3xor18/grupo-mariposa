package com.grupomariposa.orders.application.port.out;

import com.grupomariposa.orders.domain.model.TaxRateSchedule;

public interface TaxRateSource {

    TaxRateSchedule approvedSchedule();

    void refresh();
}
