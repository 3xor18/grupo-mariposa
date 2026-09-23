package com.grupomariposa.orders.infrastructure.config;

import com.grupomariposa.orders.domain.model.CurrencyCatalog;
import com.grupomariposa.orders.domain.model.CurrencyCode;
import com.grupomariposa.orders.domain.model.MarketCatalog;
import com.grupomariposa.orders.domain.model.MarketCode;
import com.grupomariposa.orders.domain.model.MarketDefinition;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

public final class PlatformCatalogParser {

    private static final String ENTRY_SEPARATOR = ",";
    private static final String FIELD_SEPARATOR = ":";
    private static final Pattern DIGITS = Pattern.compile("^\\d{1,2}$");
    private static final int CURRENCY_FIELDS = 2;
    private static final int MARKET_FIELDS = 3;
    private static final String MALFORMED_CURRENCY =
            "platform.currencies entry '%s' must look like CODE:DECIMALS";
    private static final String MALFORMED_MARKET =
            "platform.markets entry '%s' must look like CODE:CURRENCY:LOCALE";
    private static final String DUPLICATED_CURRENCY = "Currency %s is configured more than once";
    private static final String INVALID_LOCALE = "platform.markets entry '%s' has no valid locale";

    private PlatformCatalogParser() {
    }

    public static MarketCatalog parse(final PlatformProperties properties) {
        final CurrencyCatalog currencies = currencies(properties.currencies());
        try {
            return new MarketCatalog(markets(properties.markets()), currencies);
        } catch (IllegalArgumentException invalid) {
            throw new IllegalStateException(invalid.getMessage(), invalid);
        }
    }

    static CurrencyCatalog currencies(final String raw) {
        final Map<CurrencyCode, Integer> digits = new LinkedHashMap<>();
        for (final String entry : entries(raw)) {
            final String[] fields = entry.split(FIELD_SEPARATOR, -1);
            if (fields.length != CURRENCY_FIELDS || !DIGITS.matcher(fields[1]).matches()) {
                throw new IllegalStateException(MALFORMED_CURRENCY.formatted(entry));
            }
            final CurrencyCode code = CurrencyCode.parse(fields[0]).orElseThrow(() ->
                    new IllegalStateException(MALFORMED_CURRENCY.formatted(entry)));
            if (digits.put(code, Integer.parseInt(fields[1])) != null) {
                throw new IllegalStateException(DUPLICATED_CURRENCY.formatted(code));
            }
        }
        try {
            return new CurrencyCatalog(digits);
        } catch (IllegalArgumentException invalid) {
            throw new IllegalStateException(invalid.getMessage(), invalid);
        }
    }

    private static List<MarketDefinition> markets(final String raw) {
        return entries(raw).stream().map(PlatformCatalogParser::market).toList();
    }

    private static MarketDefinition market(final String entry) {
        final String[] fields = entry.split(FIELD_SEPARATOR, -1);
        if (fields.length != MARKET_FIELDS) {
            throw new IllegalStateException(MALFORMED_MARKET.formatted(entry));
        }
        final MarketCode code = MarketCode.parse(fields[0]).orElseThrow(() ->
                new IllegalStateException(MALFORMED_MARKET.formatted(entry)));
        final CurrencyCode currency = CurrencyCode.parse(fields[1]).orElseThrow(() ->
                new IllegalStateException(MALFORMED_MARKET.formatted(entry)));
        final Locale locale = Locale.forLanguageTag(fields[2]);
        if (locale.getLanguage().isEmpty()) {
            throw new IllegalStateException(INVALID_LOCALE.formatted(entry));
        }
        return new MarketDefinition(code, currency, locale);
    }

    private static List<String> entries(final String raw) {
        return Arrays.stream(raw.split(ENTRY_SEPARATOR))
                .map(String::trim)
                .filter(entry -> !entry.isEmpty())
                .toList();
    }
}
