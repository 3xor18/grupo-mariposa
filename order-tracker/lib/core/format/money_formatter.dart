import 'package:intl/intl.dart';
import 'package:order_tracker/core/format/app_locale.dart';
import 'package:order_tracker/core/format/currency_locales.dart';
import 'package:order_tracker/core/money/money.dart';

final class MoneyFormatter {
  MoneyFormatter();

  final Map<String, NumberFormat> _formats = {};

  String format(Money money) {
    final format = _formats.putIfAbsent(money.currency, () => _create(money.currency));
    return format.format(money.amount);
  }

  NumberFormat _create(String currency) {
    return NumberFormat.simpleCurrency(
      locale: CurrencyLocales.byCurrency[currency] ?? AppLocale.languageCode,
      name: currency,
      decimalDigits: Money.fractionDigits,
    );
  }
}
