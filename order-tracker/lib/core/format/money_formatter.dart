import 'package:intl/intl.dart';
import 'package:order_tracker/core/format/app_locale.dart';

final class MoneyFormatter {
  MoneyFormatter();

  static const _fractionDigits = 2;
  static const _localeByCurrency = {'MXN': 'es_MX', 'COP': 'es_CO', 'PEN': 'es_PE'};

  final Map<String, NumberFormat> _formats = {};

  String format(double amount, String currency) {
    final format = _formats.putIfAbsent(currency, () => _create(currency));
    return format.format(amount);
  }

  NumberFormat _create(String currency) {
    return NumberFormat.simpleCurrency(
      locale: _localeByCurrency[currency] ?? AppLocale.languageCode,
      name: currency,
      decimalDigits: _fractionDigits,
    );
  }
}
