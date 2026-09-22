import 'package:flutter_test/flutter_test.dart';
import 'package:intl/date_symbol_data_local.dart';
import 'package:order_tracker/core/format/app_formatters.dart';
import 'package:order_tracker/core/money/money.dart';

void main() {
  late AppFormatters formatters;

  setUpAll(() => initializeDateFormatting('es'));

  setUp(() => formatters = AppFormatters());

  String money(String amount, String currency) {
    return formatters.money(Money.parse(amount, currency: currency));
  }

  group('money', () {
    test('should format MXN with the Mexican locale', () {
      expect(money('2100.11', 'MXN'), r'$2,100.11');
    });

    test('should format COP with the Colombian locale', () {
      expect(money('2100.11', 'COP'), contains('2.100,11'));
    });

    test('should format PEN with the Peruvian locale', () {
      final formatted = money('2100.11', 'PEN');
      expect(formatted, contains('S/'));
      expect(formatted, contains('2.100,11'));
    });

    test('should fall back to the Spanish locale for other currencies', () {
      expect(money('10', 'USD'), contains('10,00'));
    });

    test('should format negative and whole amounts with two decimals', () {
      expect(money('-5.5', 'MXN'), contains('5.50'));
      expect(money('-5.5', 'MXN'), contains('-'));
      expect(money('7', 'MXN'), r'$7.00');
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
