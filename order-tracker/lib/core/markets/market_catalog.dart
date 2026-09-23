import 'package:equatable/equatable.dart';
import 'package:order_tracker/core/format/app_locale.dart';
import 'package:order_tracker/core/json/json_map.dart';

abstract final class _MarketFields {
  static const code = 'code';
  static const currency = 'currency';
  static const locale = 'locale';
  static const name = 'name';
}

abstract final class _CatalogFields {
  static const markets = 'markets';
  static const currencies = 'currencies';
}

final class MarketDefinition extends Equatable {
  const MarketDefinition({
    required this.code,
    required this.currency,
    required this.locale,
    required this.name,
  });

  factory MarketDefinition.fromJson(JsonMap json) {
    final code = json.requireString(_MarketFields.code);
    return MarketDefinition(
      code: code,
      currency: json.requireString(_MarketFields.currency),
      locale: json.requireString(_MarketFields.locale),
      name: json.optionalString(_MarketFields.name) ?? code,
    );
  }

  final String code;
  final String currency;
  final String locale;
  final String name;

  @override
  List<Object?> get props => [code, currency, locale, name];
}

final class MarketCatalog extends Equatable {
  const MarketCatalog({required this.markets, required this.currencyDigits});

  factory MarketCatalog.fromJson(JsonMap json) {
    final currencies = json.requireObject(_CatalogFields.currencies);
    return MarketCatalog(
      markets: json
          .requireObjectList(_CatalogFields.markets)
          .map(MarketDefinition.fromJson)
          .toList(growable: false),
      currencyDigits: {
        for (final currency in currencies.raw.keys) currency: currencies.requireInt(currency),
      },
    );
  }

  static const defaultFractionDigits = 2;

  final List<MarketDefinition> markets;
  final Map<String, int> currencyDigits;

  int fractionDigitsOf(String currency) => currencyDigits[currency] ?? defaultFractionDigits;

  String nameOf(String marketCode) =>
      _marketWhere((market) => market.code == marketCode)?.name ?? marketCode;

  String localeOf(String currency) {
    return _marketWhere((market) => market.currency == currency)?.locale ?? AppLocale.languageCode;
  }

  MarketDefinition? _marketWhere(bool Function(MarketDefinition market) test) {
    for (final market in markets) {
      if (test(market)) {
        return market;
      }
    }
    return null;
  }

  @override
  List<Object?> get props => [markets, currencyDigits];
}
