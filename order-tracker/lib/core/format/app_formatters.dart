import 'package:intl/intl.dart';
import 'package:order_tracker/core/format/app_locale.dart';
import 'package:order_tracker/core/format/money_formatter.dart';
import 'package:order_tracker/core/markets/market_catalog.dart';
import 'package:order_tracker/core/money/money.dart';
import 'package:order_tracker/core/money/unit_price.dart';

final class AppFormatters {
  AppFormatters(MarketCatalog catalog)
    : _money = MoneyFormatter(catalog),
      _dateTime = DateFormat.yMMMd(AppLocale.languageCode).add_Hm(),
      _percent = NumberFormat.decimalPercentPattern(
        locale: AppLocale.languageCode,
        decimalDigits: _percentDigits,
      );

  static const _percentDigits = 1;

  final MoneyFormatter _money;
  final DateFormat _dateTime;
  final NumberFormat _percent;

  String money(Money value) => _money.format(value);

  String unitPrice(UnitPrice value) => _money.formatUnitPrice(value);

  String dateTime(DateTime value) => _dateTime.format(value.toLocal());

  String percent(double rate) => _percent.format(rate);
}
