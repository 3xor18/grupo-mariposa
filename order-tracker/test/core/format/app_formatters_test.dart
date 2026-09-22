import 'package:flutter_test/flutter_test.dart';
import 'package:intl/date_symbol_data_local.dart';
import 'package:order_tracker/core/format/app_formatters.dart';

void main() {
  late AppFormatters formatters;

  setUpAll(() => initializeDateFormatting('es'));

  setUp(() => formatters = AppFormatters());

  group('money', () {
    test('should format MXN with the Mexican locale', () {
      expect(formatters.currency(2100.11, 'MXN'), r'$2,100.11');
    });

    test('should format COP with the Colombian locale', () {
      expect(formatters.currency(2100.11, 'COP'), contains('2.100,11'));
    });

    test('should format PEN with the Peruvian locale', () {
      final formatted = formatters.currency(2100.11, 'PEN');
      expect(formatted, contains('S/'));
      expect(formatted, contains('2.100,11'));
    });

    test('should fall back to the Spanish locale for other currencies', () {
      expect(formatters.currency(10, 'USD'), contains('10,00'));
      expect(formatters.money.format(10, 'USD'), formatters.currency(10, 'USD'));
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
