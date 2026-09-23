abstract final class ScaledDecimal {
  static const _radix = 10;
  static const _minus = '-';
  static const _signGroup = 1;
  static const _wholeGroup = 2;
  static const _fractionGroup = 3;
  static const _invalidAmount = 'Invalid decimal amount';

  static int parse(String decimal, {required int fractionDigits}) {
    final pattern = RegExp(
      r'^(-?)(\d+)(?:\.(\d{1,'
      '$fractionDigits'
      r'}))?$',
    );
    final match = pattern.firstMatch(decimal.trim());
    if (match == null) {
      throw FormatException(_invalidAmount, decimal);
    }
    final whole = int.parse(match.group(_wholeGroup) ?? '');
    final fraction = (match.group(_fractionGroup) ?? '').padRight(fractionDigits, '0');
    final magnitude = whole * scaleOf(fractionDigits) + int.parse(fraction);
    return match.group(_signGroup) == _minus ? -magnitude : magnitude;
  }

  static int scaleOf(int fractionDigits) {
    var scale = 1;
    for (var digit = 0; digit < fractionDigits; digit++) {
      scale *= _radix;
    }
    return scale;
  }

  static int significantFractionDigits(
    int scaledValue, {
    required int fractionDigits,
    required int minimumDigits,
  }) {
    var fraction = scaledValue.abs() % scaleOf(fractionDigits);
    var digits = fractionDigits;
    while (digits > minimumDigits && fraction % _radix == 0) {
      fraction ~/= _radix;
      digits--;
    }
    return digits;
  }
}
