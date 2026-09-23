import 'package:intl/intl.dart';
import 'package:order_tracker/core/format/app_locale.dart';
import 'package:order_tracker/core/format/currency_locales.dart';
import 'package:order_tracker/core/money/money.dart';
import 'package:order_tracker/core/money/unit_price.dart';

final class MoneyFormatter {
  MoneyFormatter();

  final Map<(String, int), NumberFormat> _formats = {};

  String format(Money money) => _format(money.amount, money.currency, Money.fractionDigits);

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
    return NumberFormat.simpleCurrency(
      locale: CurrencyLocales.byCurrency[currency] ?? AppLocale.languageCode,
      name: currency,
      decimalDigits: fractionDigits,
    );
  }
}
