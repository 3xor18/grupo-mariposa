import 'package:equatable/equatable.dart';
import 'package:order_tracker/core/money/scaled_decimal.dart';

final class Money extends Equatable {
  const Money({required this.minorUnits, required this.currency, required this.fractionDigits});

  factory Money.parse(String decimal, {required String currency, required int fractionDigits}) {
    return Money(
      minorUnits: ScaledDecimal.parse(decimal, fractionDigits: fractionDigits),
      currency: currency,
      fractionDigits: fractionDigits,
    );
  }

  final int minorUnits;
  final String currency;
  final int fractionDigits;

  double get amount => minorUnits / ScaledDecimal.scaleOf(fractionDigits);

  @override
  List<Object?> get props => [minorUnits, currency, fractionDigits];
}
