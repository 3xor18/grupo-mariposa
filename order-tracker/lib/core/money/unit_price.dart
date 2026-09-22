import 'package:equatable/equatable.dart';
import 'package:order_tracker/core/money/money.dart';
import 'package:order_tracker/core/money/scaled_decimal.dart';

final class UnitPrice extends Equatable {
  const UnitPrice({required this.tenThousandths, required this.currency});

  factory UnitPrice.parse(String decimal, {required String currency}) {
    return UnitPrice(
      tenThousandths: ScaledDecimal.parse(decimal, fractionDigits: fractionDigits),
      currency: currency,
    );
  }

  static const fractionDigits = 4;

  final int tenThousandths;
  final String currency;

  double get amount => tenThousandths / ScaledDecimal.scaleOf(fractionDigits);

  int get displayFractionDigits => ScaledDecimal.significantFractionDigits(
    tenThousandths,
    fractionDigits: fractionDigits,
    minimumDigits: Money.fractionDigits,
  );

  @override
  List<Object?> get props => [tenThousandths, currency];
}
