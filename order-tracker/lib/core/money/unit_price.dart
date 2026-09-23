import 'package:equatable/equatable.dart';
import 'package:order_tracker/core/money/scaled_decimal.dart';

final class UnitPrice extends Equatable {
  const UnitPrice({
    required this.tenThousandths,
    required this.currency,
    required this.currencyDigits,
  });

  factory UnitPrice.parse(String decimal, {required String currency, required int currencyDigits}) {
    return UnitPrice(
      tenThousandths: ScaledDecimal.parse(decimal, fractionDigits: fractionDigits),
      currency: currency,
      currencyDigits: currencyDigits,
    );
  }

  static const fractionDigits = 4;

  final int tenThousandths;
  final String currency;
  final int currencyDigits;

  double get amount => tenThousandths / ScaledDecimal.scaleOf(fractionDigits);

  int get displayFractionDigits => ScaledDecimal.significantFractionDigits(
    tenThousandths,
    fractionDigits: fractionDigits,
    minimumDigits: currencyDigits,
  );

  @override
  List<Object?> get props => [tenThousandths, currency, currencyDigits];
}
