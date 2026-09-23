package com.grupomariposa.orders.infrastructure.config;

import com.grupomariposa.orders.application.validation.ContractRules;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.regex.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.validation")
public record ValidationProperties(
        @NotBlank String productIdPattern,
        @NotBlank String clientIdPattern,
        @Min(1) int priceMaxPrecision,
        @Min(0) int priceMaxScale,
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal priceMaxValue) {

    public ContractRules rules() {
        return new ContractRules(Pattern.compile(productIdPattern),
                Pattern.compile(clientIdPattern), priceMaxPrecision, priceMaxScale,
                priceMaxValue);
    }
}
