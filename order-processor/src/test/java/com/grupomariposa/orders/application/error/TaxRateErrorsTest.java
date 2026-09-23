package com.grupomariposa.orders.application.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TaxRateErrorsTest {

    @Test
    void should_describe_tax_rate_errors() {
        assertThat(new TaxRateNotFoundException("abc")).hasMessage("Tax rate abc does not exist");
        assertThat(new TaxRateConflictException("changed")).hasMessage("changed");
    }
}
