package com.grupomariposa.orders.infrastructure.web;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.api.orders")
public record OrdersApiProperties(@Min(1) int defaultPageSize, @Min(1) int maxPageSize,
                                  @Min(1) long maxOffset) {

    @AssertTrue(message = "default page size cannot exceed the maximum page size")
    public boolean isDefaultWithinMaximum() {
        return defaultPageSize <= maxPageSize;
    }
}
