import 'package:flutter_test/flutter_test.dart';
import 'package:intl/date_symbol_data_local.dart';
import 'package:order_tracker/core/format/app_formatters.dart';
import 'package:order_tracker/core/money/money.dart';
import 'package:order_tracker/core/money/unit_price.dart';

import '../../fixtures/market_fixtures.dart';

void main() {
  late AppFormatters formatters;

  setUpAll(() => initializeDateFormatting('es'));

  setUp(() => formatters = AppFormatters(testCatalog));

  String money(String amount, String currency) {
    final digits = testCatalog.fractionDigitsOf(currency);
    return formatters.money(Money.parse(amount, currency: currency, fractionDigits: digits));
  }

  String unitPrice(String amount, String currency) {
    final digits = testCatalog.fractionDigitsOf(currency);
    return formatters.unitPrice(
      UnitPrice.parse(amount, currency: currency, currencyDigits: digits),
    );
  }

  group('money', () {
    test('should format MXN with the Mexican locale data', () {
      expect(money('2100.11', 'MXN'), r'$2,100.11');
      expect(money('7', 'MXN'), r'$7.00');
      expect(money('-5.5', 'MXN'), contains('-'));
    });

    test('should format CLP without decimals using Chilean grouping', () {
      expect(money('45371', 'CLP'), r'$ 45.371');
      expect(money('1234567', 'CLP'), r'$ 1.234.567');
    });

    test('should format USD for Ecuador with two decimals', () {
      expect(money('1234.5', 'USD'), r'$ 1.234,50');
    });

    test('should format COP and PEN with their symbols before the amount', () {
      expect(money('2100.11', 'COP'), r'$ 2.100,11');
      expect(money('2100.11', 'PEN'), 'S/ 2.100,11');
    });

    test('should show the code of a currency outside the catalog with two decimals', () {
      final formatted = money('10', 'XYZ');
      expect(formatted, contains('XYZ'));
      expect(formatted, contains('10,00'));
    });
  });

  group('unit price', () {
    test('should show up to four decimals trimming trailing zeros to the currency digits', () {
      expect(unitPrice('12.3456', 'MXN'), r'$12.3456');
      expect(unitPrice('12.345', 'MXN'), r'$12.345');
      expect(unitPrice('12.3', 'MXN'), r'$12.30');
      expect(unitPrice('1234.5000', 'MXN'), r'$1,234.50');
    });

    test('should show whole CLP unit prices without decimals', () {
      expect(unitPrice('990', 'CLP'), r'$ 990');
      expect(unitPrice('990.5', 'CLP'), r'$ 990,5');
    });
  });

  test('should format dates in Spanish', () {
    final formatted = formatters.dateTime(DateTime(2026, 9, 18, 15, 42));
    expect(formatted, contains('2026'));
    expect(formatted, contains('15:42'));
    expect(formatted, contains('sept'));
  });

  test('should format rates as percentages', () {
    expect(formatters.percent(0.16), contains('16'));
    expect(formatters.percent(0.16), contains('%'));
  });
}
