import 'package:order_tracker/core/markets/market_catalog.dart';

const testCatalog = MarketCatalog(
  markets: [
    MarketDefinition(code: 'MX', currency: 'MXN', locale: 'es-MX', name: 'México'),
    MarketDefinition(code: 'CO', currency: 'COP', locale: 'es-CO', name: 'Colombia'),
    MarketDefinition(code: 'PE', currency: 'PEN', locale: 'es-PE', name: 'Perú'),
    MarketDefinition(code: 'CL', currency: 'CLP', locale: 'es-CL', name: 'Chile'),
    MarketDefinition(code: 'EC', currency: 'USD', locale: 'es-EC', name: 'Ecuador'),
  ],
  currencyDigits: {'MXN': 2, 'COP': 2, 'PEN': 2, 'CLP': 0, 'USD': 2},
);

Map<String, Object?> testCatalogJson() => {
  'markets': [
    for (final market in testCatalog.markets)
      {
        'code': market.code,
        'currency': market.currency,
        'locale': market.locale,
        'name': market.name,
      },
  ],
  'currencies': testCatalog.currencyDigits,
};
