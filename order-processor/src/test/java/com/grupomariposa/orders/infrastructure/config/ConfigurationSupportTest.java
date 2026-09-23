package com.grupomariposa.orders.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import com.grupomariposa.orders.domain.Currencies;
import com.grupomariposa.orders.domain.DomainFixtures;
import com.grupomariposa.orders.domain.Markets;
import com.grupomariposa.orders.domain.model.MarketCatalog;
import com.grupomariposa.orders.domain.model.Rate;
import com.grupomariposa.orders.domain.model.TaxCategory;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.SpringApplication;
import org.springframework.mock.env.MockEnvironment;

class ConfigurationSupportTest {

    private static final Map<TaxCategory, BigDecimal> MX_RATES = Map.of(
            TaxCategory.STANDARD, new BigDecimal("0.16"),
            TaxCategory.REDUCED, new BigDecimal("0.08"),
            TaxCategory.EXEMPT, new BigDecimal("0.00"));

    @Test
    void should_build_domain_pricing_rules_from_properties() {
        final PricingProperties pricing = new PricingProperties(Map.of("MX", MX_RATES),
                new PricingProperties.WholesaleDiscount(new BigDecimal("0.03"), 20));

        assertThat(pricing.taxRateTable(mexicoOnly()).rateFor(Markets.MX, TaxCategory.STANDARD)
                .value()).isEqualByComparingTo("0.16");
        assertThat(pricing.discountRule().rate()).isEqualTo(new Rate(new BigDecimal("0.03")));
        assertThat(pricing.discountRule().minimumQuantity()).isEqualTo(20);
    }

    @Test
    void should_fail_startup_when_a_catalog_market_has_no_complete_tax_rates() {
        final PricingProperties mexicoRates = new PricingProperties(Map.of("MX", MX_RATES),
                new PricingProperties.WholesaleDiscount(BigDecimal.ZERO, 1));
        final PricingProperties missingCategory = new PricingProperties(
                Map.of("MX", Map.of(TaxCategory.STANDARD, BigDecimal.ONE)),
                new PricingProperties.WholesaleDiscount(BigDecimal.ZERO, 1));

        assertThatIllegalStateException()
                .isThrownBy(() -> mexicoRates.taxRateTable(DomainFixtures.MARKETS));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> missingCategory.taxRateTable(mexicoOnly()));
    }

    @Test
    void should_parse_the_platform_catalog() {
        final MarketCatalog catalog = PlatformCatalogParser.parse(new PlatformProperties(
                " MX:MXN:es-MX, CL:CLP:es-CL ,EC:USD:es-EC", "MXN:2,CLP:0,USD:2"));

        assertThat(catalog.supportedMarkets()).containsExactly(Markets.MX, Markets.CL,
                Markets.EC);
        assertThat(catalog.fractionDigitsOf(Markets.CL)).isZero();
        assertThat(catalog.accepts(Markets.EC, Currencies.USD)).isTrue();
    }

    @ParameterizedTest(name = "markets={0} currencies={1}")
    @CsvSource(delimiter = '|', value = {
        "MX:MXN|MXN:2",
        "MEX:MXN:es-MX|MXN:2",
        "MX:mxn:es-MX|MXN:2",
        "MX:MXN:|MXN:2",
        "MX:MXN:es-MX,MX:MXN:es-MX|MXN:2",
        "MX:ARS:es-MX|MXN:2",
        "MX:MXN:es-MX|MXN",
        "MX:MXN:es-MX|MXN:two",
        "MX:MXN:es-MX|mxn:2",
        "MX:MXN:es-MX|MXN:2,MXN:2",
        "MX:MXN:es-MX|MXN:9"
    })
    void should_fail_fast_on_malformed_platform_catalog(final String markets,
                                                       final String currencies) {
        assertThatIllegalStateException().isThrownBy(() ->
                PlatformCatalogParser.parse(new PlatformProperties(markets, currencies)));
    }

    private static MarketCatalog mexicoOnly() {
        return new MarketCatalog(List.of(DomainFixtures.market(Markets.MX, Currencies.MXN,
                "es-MX")), DomainFixtures.CURRENCIES);
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
