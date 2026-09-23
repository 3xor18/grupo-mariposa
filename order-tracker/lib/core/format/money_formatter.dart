import 'package:intl/intl.dart';
import 'package:order_tracker/core/format/app_locale.dart';
import 'package:order_tracker/core/markets/market_catalog.dart';
import 'package:order_tracker/core/money/money.dart';
import 'package:order_tracker/core/money/unit_price.dart';

final class MoneyFormatter {
  MoneyFormatter(this._catalog);

  static const _symbolPrefixPattern = '¤ #,##0';
  static const _decimalSeparator = '.';
  static const _fractionPlaceholder = '0';

  final MarketCatalog _catalog;
  final Map<(String, int), NumberFormat> _formats = {};

  String format(Money money) => _format(money.amount, money.currency, money.fractionDigits);

  String formatUnitPrice(UnitPrice price) {
    return _format(price.amount, price.currency, price.displayFractionDigits);
  }

  String _format(double amount, String currency, int fractionDigits) {
    final format = _formats.putIfAbsent(
      (currency, fractionDigits),
      () => _create(currency, fractionDigits),
    );
    return format.format(amount);
  }

  NumberFormat _create(String currency, int fractionDigits) {
    final locale = Intl.canonicalizedLocale(_catalog.localeOf(currency));
    if (NumberFormat.localeExists(locale)) {
      return NumberFormat.simpleCurrency(
        locale: locale,
        name: currency,
        decimalDigits: fractionDigits,
      );
    }
    return NumberFormat.currency(
      locale: AppLocale.languageCode,
      name: currency,
      symbol: _symbolOf(currency),
      customPattern: _prefixedPattern(fractionDigits),
      decimalDigits: fractionDigits,
    );
  }

  String _symbolOf(String currency) {
    return NumberFormat.simpleCurrency(
      locale: AppLocale.languageCode,
      name: currency,
    ).currencySymbol;
  }

  String _prefixedPattern(int fractionDigits) {
    if (fractionDigits == 0) {
      return _symbolPrefixPattern;
    }
    return '$_symbolPrefixPattern$_decimalSeparator${_fractionPlaceholder * fractionDigits}';
  }
}
