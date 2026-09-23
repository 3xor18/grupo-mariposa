import 'package:flutter_test/flutter_test.dart';
import 'package:order_tracker/core/money/money.dart';

void main() {
  Money parse(String amount) => Money.parse(amount, currency: 'MXN', fractionDigits: 2);

  Money pesos(String amount) => Money.parse(amount, currency: 'CLP', fractionDigits: 0);

  group('Money.parse', () {
    test('should convert decimal text into integer minor units', () {
      expect(
        parse('2100.11'),
        const Money(minorUnits: 210011, currency: 'MXN', fractionDigits: 2),
      );
      expect(parse('35.5').minorUnits, 3550);
      expect(parse('82').minorUnits, 8200);
      expect(parse(' 0.07 ').minorUnits, 7);
      expect(parse('1836.0').minorUnits, 183600);
      expect(parse('1.230').minorUnits, 123);
    });

    test('should use the currency fraction digits for zero decimal currencies', () {
      expect(pesos('45371').minorUnits, 45371);
      expect(pesos('45371.00').minorUnits, 45371);
      expect(pesos('45371').amount, 45371);
      expect(() => pesos('45371.5'), throwsFormatException);
    });

    test('should keep the sign of negative amounts', () {
      expect(parse('-55.08').minorUnits, -5508);
      expect(parse('-0.5').minorUnits, -50);
    });

    test('should avoid binary floating point drift', () {
      expect(parse('0.1').minorUnits + parse('0.2').minorUnits, parse('0.3').minorUnits);
    });

    test('should reject text with more significant decimals than the currency allows', () {
      for (final invalid in ['', 'abc', '1.234', '1e+21', '1,50', '.5', '--1', '1.']) {
        expect(() => parse(invalid), throwsFormatException, reason: invalid);
      }
    });
  });

  test('should expose the amount for display and compare by value', () {
    expect(parse('2100.11').amount, 2100.11);
    expect(parse('1'), isNot(Money.parse('1', currency: 'COP', fractionDigits: 2)));
  });
}
