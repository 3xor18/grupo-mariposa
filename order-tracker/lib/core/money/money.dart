import 'package:equatable/equatable.dart';

final class Money extends Equatable {
  const Money({required this.minorUnits, required this.currency});

  factory Money.parse(String decimal, {required String currency}) {
    final match = _decimalPattern.firstMatch(decimal.trim());
    if (match == null) {
      throw FormatException(_invalidAmount, decimal);
    }
    final whole = int.parse(match.group(_wholeGroup) ?? '');
    final fraction = (match.group(_fractionGroup) ?? '').padRight(fractionDigits, '0');
    final magnitude = whole * _scale + int.parse(fraction);
    final negative = match.group(_signGroup) == _minus;
    return Money(minorUnits: negative ? -magnitude : magnitude, currency: currency);
  }

  static const fractionDigits = 2;
  static const _scale = 100;
  static const _minus = '-';
  static const _signGroup = 1;
  static const _wholeGroup = 2;
  static const _fractionGroup = 3;
  static const _invalidAmount = 'Invalid monetary amount';
  static final _decimalPattern = RegExp(r'^(-?)(\d+)(?:\.(\d{1,2}))?$');

  final int minorUnits;
  final String currency;

  double get amount => minorUnits / _scale;

  @override
  List<Object?> get props => [minorUnits, currency];
}
