package com.grupomariposa.orders.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import com.grupomariposa.orders.domain.model.Currency;
import com.grupomariposa.orders.domain.model.Market;
import com.grupomariposa.orders.domain.model.Rate;
import com.grupomariposa.orders.domain.model.TaxCategory;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.mock.env.MockEnvironment;

class ConfigurationSupportTest {

    private static final Map<TaxCategory, BigDecimal> MX_RATES = Map.of(
            TaxCategory.STANDARD, new BigDecimal("0.16"),
            TaxCategory.REDUCED, new BigDecimal("0.08"),
            TaxCategory.EXEMPT, new BigDecimal("0.00"));

    @Test
    void should_build_domain_pricing_rules_from_properties() {
        final PricingProperties pricing = new PricingProperties(Map.of(Market.MX, MX_RATES),
                new PricingProperties.WholesaleDiscount(new BigDecimal("0.03"), 20),
                Map.of(Market.MX, Currency.MXN));

        assertThat(pricing.isTaxTableComplete()).isTrue();
        assertThat(pricing.taxRateTable().rateFor(Market.MX, TaxCategory.STANDARD).value())
                .isEqualByComparingTo("0.16");
        assertThat(pricing.discountRule().rate()).isEqualTo(new Rate(new BigDecimal("0.03")));
        assertThat(pricing.discountRule().minimumQuantity()).isEqualTo(20);
        assertThat(pricing.markets().accepts(Market.MX, Currency.MXN)).isTrue();
    }

    @Test
    void should_flag_tables_that_do_not_cover_supported_markets() {
        final PricingProperties missingMarket = new PricingProperties(Map.of(Market.MX, MX_RATES),
                new PricingProperties.WholesaleDiscount(BigDecimal.ZERO, 1),
                Map.of(Market.MX, Currency.MXN, Market.PE, Currency.PEN));
        final PricingProperties missingCategory = new PricingProperties(
                Map.of(Market.MX, Map.of(TaxCategory.STANDARD, BigDecimal.ONE)),
                new PricingProperties.WholesaleDiscount(BigDecimal.ZERO, 1),
                Map.of(Market.MX, Currency.MXN));
        final PricingProperties unset = new PricingProperties(null, null, null);

        assertThat(missingMarket.isTaxTableComplete()).isFalse();
        assertThat(missingCategory.isTaxTableComplete()).isFalse();
        assertThat(unset.isTaxTableComplete()).isFalse();
    }

    @Test
    void should_read_secrets_only_from_the_environment() {
        final EnvironmentSecrets secrets = new EnvironmentSecrets(new MockEnvironment()
                .withProperty("OAUTH_CLIENT_SECRET", "s3cr3t")
                .withProperty("PII_PREVIOUS_ENCRYPTION_KEY", " "));

        assertThat(secrets.required(EnvironmentSecrets.OAUTH_CLIENT_SECRET)).isEqualTo("s3cr3t");
        assertThat(secrets.optional(EnvironmentSecrets.PII_PREVIOUS_ENCRYPTION_KEY)).isEmpty();
        assertThatIllegalStateException()
                .isThrownBy(() -> secrets.required(EnvironmentSecrets.PII_ENCRYPTION_KEY))
                .withMessage("Secret PII_ENCRYPTION_KEY must be provided through the environment");
    }

    @Test
    void should_disable_config_server_when_no_url_is_given() {
        final MockEnvironment standalone = new MockEnvironment();
        final MockEnvironment centralized = new MockEnvironment()
                .withProperty(ConfigServerActivation.CONFIG_SERVER_URL, "http://config:8888");
        final ConfigServerActivation activation = new ConfigServerActivation();

        activation.postProcessEnvironment(standalone, new SpringApplication());
        activation.postProcessEnvironment(centralized, new SpringApplication());

        assertThat(standalone.getProperty(ConfigServerActivation.ENABLED_PROPERTY))
                .isEqualTo("false");
        assertThat(centralized.getProperty(ConfigServerActivation.ENABLED_PROPERTY)).isNull();
        assertThat(activation.getOrder()).isNegative();
    }
}
