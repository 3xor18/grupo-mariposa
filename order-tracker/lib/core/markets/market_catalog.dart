import 'package:equatable/equatable.dart';
import 'package:order_tracker/core/config/config_load_exception.dart';
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

abstract final class _CatalogErrors {
  static const empty = 'The market catalog is empty';
  static const duplicateMarket = 'Duplicated market';
  static const invalidMarket = 'Invalid market code';
  static const invalidCurrency = 'Invalid currency code';
  static const invalidDigits = 'Currency fraction digits out of range for';
  static const invalidLocale = 'Invalid locale for market';
  static const undeclaredCurrency = 'Market currency without declared digits:';
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
    final catalog = MarketCatalog(
      markets: json
          .requireObjectList(_CatalogFields.markets)
          .map(MarketDefinition.fromJson)
          .toList(growable: false),
      currencyDigits: {
        for (final currency in currencies.raw.keys) currency: currencies.requireInt(currency),
      },
    );
    return catalog.._validate();
  }

  static const defaultFractionDigits = 2;
  static const maxFractionDigits = 4;
  static final _marketCode = RegExp(r'^[A-Z]{2}$');
  static final _currencyCode = RegExp(r'^[A-Z]{3}$');
  static final _locale = RegExp(r'^[a-z]{2}-[A-Z]{2}$');

  final List<MarketDefinition> markets;
  final Map<String, int> currencyDigits;

  int fractionDigitsOf(String currency) => currencyDigits[currency] ?? defaultFractionDigits;

  String nameOf(String marketCode) =>
      _marketWhere((market) => market.code == marketCode)?.name ?? marketCode;

  String localeOf(String currency) {
    return _marketWhere((market) => market.currency == currency)?.locale ?? AppLocale.languageCode;
  }

  void _validate() {
    if (markets.isEmpty) {
      _reject(_CatalogErrors.empty);
    }
    currencyDigits.forEach(_validateCurrency);
    final seen = <String>{};
    for (final market in markets) {
      _validateMarket(market);
      if (!seen.add(market.code)) {
        _reject('${_CatalogErrors.duplicateMarket} ${market.code}');
      }
    }
  }

  void _validateCurrency(String currency, int digits) {
    if (!_currencyCode.hasMatch(currency)) {
      _reject('${_CatalogErrors.invalidCurrency} $currency');
    }
    if (digits < 0 || digits > maxFractionDigits) {
      _reject('${_CatalogErrors.invalidDigits} $currency');
    }
  }

  void _validateMarket(MarketDefinition market) {
    if (!_marketCode.hasMatch(market.code)) {
      _reject('${_CatalogErrors.invalidMarket} ${market.code}');
    }
    if (!_locale.hasMatch(market.locale)) {
      _reject('${_CatalogErrors.invalidLocale} ${market.code}');
    }
    if (!currencyDigits.containsKey(market.currency)) {
      _reject('${_CatalogErrors.undeclaredCurrency} ${market.currency}');
    }
  }

  static Never _reject(String detail) {
    throw ConfigLoadException(ConfigLoadFailure.invalidMarketCatalog, detail: detail);
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
