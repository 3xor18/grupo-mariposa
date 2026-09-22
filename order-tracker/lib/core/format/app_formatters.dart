import 'package:intl/intl.dart';
import 'package:order_tracker/core/format/app_locale.dart';
import 'package:order_tracker/core/format/money_formatter.dart';

final class AppFormatters {
  AppFormatters()
    : money = MoneyFormatter(),
      _dateTime = DateFormat.yMMMd(AppLocale.languageCode).add_Hm(),
      _percent = NumberFormat.decimalPercentPattern(
        locale: AppLocale.languageCode,
        decimalDigits: _percentDigits,
      );

  static const _percentDigits = 1;

  final MoneyFormatter money;
  final DateFormat _dateTime;
  final NumberFormat _percent;

  String currency(double amount, String currency) => money.format(amount, currency);

  String dateTime(DateTime value) => _dateTime.format(value.toLocal());

  String percent(double rate) => _percent.format(rate);
}
