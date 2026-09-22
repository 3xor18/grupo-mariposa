import 'package:equatable/equatable.dart';

sealed class OrderSearchEvent extends Equatable {
  const OrderSearchEvent();

  @override
  List<Object?> get props => [];
}

final class OrderSearchSubmitted extends OrderSearchEvent {
  const OrderSearchSubmitted(this.orderId);

  final String orderId;

  @override
  List<Object?> get props => [orderId];
}

final class OrderSearchRetried extends OrderSearchEvent {
  const OrderSearchRetried();
}

final class OrderSearchCleared extends OrderSearchEvent {
  const OrderSearchCleared();
}
