import 'package:equatable/equatable.dart';

final class MarketCode extends Equatable {
  const MarketCode(this.value);

  final String value;

  @override
  List<Object?> get props => [value];
}
