import 'package:equatable/equatable.dart';
import 'package:order_tracker/core/money/scaled_decimal.dart';

final class Money extends Equatable {
  const Money({required this.minorUnits, required this.currency});

  factory Money.parse(String decimal, {required String currency}) {
    return Money(
      minorUnits: ScaledDecimal.parse(decimal, fractionDigits: fractionDigits),
      currency: currency,
    );
  }

  static const fractionDigits = 2;

  final int minorUnits;
  final String currency;

  double get amount => minorUnits / ScaledDecimal.scaleOf(fractionDigits);

  @override
  List<Object?> get props => [minorUnits, currency];
}
