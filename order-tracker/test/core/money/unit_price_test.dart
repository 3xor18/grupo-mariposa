import 'package:flutter_test/flutter_test.dart';
import 'package:order_tracker/core/money/unit_price.dart';

void main() {
  UnitPrice parse(String amount) => UnitPrice.parse(amount, currency: 'MXN');

  group('UnitPrice.parse', () {
    test('should keep up to four decimals as ten-thousandths', () {
      expect(parse('12.3456'), const UnitPrice(tenThousandths: 123456, currency: 'MXN'));
      expect(parse('12.3').tenThousandths, 123000);
      expect(parse('35.5').tenThousandths, 355000);
      expect(parse('82').tenThousandths, 820000);
      expect(parse('-0.0001').tenThousandths, -1);
    });

    test('should reject more than four decimals and malformed text', () {
      for (final invalid in ['1.23456', '', 'abc', '1e+21', '1,5', '.5']) {
        expect(() => parse(invalid), throwsFormatException, reason: invalid);
      }
    });
  });

  test('should display only significant decimals with at least two', () {
    expect(parse('12.3456').displayFractionDigits, 4);
    expect(parse('12.345').displayFractionDigits, 3);
    expect(parse('12.3').displayFractionDigits, 2);
    expect(parse('35.5').displayFractionDigits, 2);
    expect(parse('82').displayFractionDigits, 2);
    expect(parse('-1.2340').displayFractionDigits, 3);
  });

  test('should expose the amount and compare by value', () {
    expect(parse('12.3456').amount, 12.3456);
    expect(parse('1'), isNot(UnitPrice.parse('1', currency: 'COP')));
  });
}
