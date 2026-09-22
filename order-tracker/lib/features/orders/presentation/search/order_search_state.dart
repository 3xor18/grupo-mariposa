import 'package:equatable/equatable.dart';
import 'package:order_tracker/core/error/app_failure.dart';
import 'package:order_tracker/features/orders/domain/entities/order.dart';

sealed class OrderSearchState extends Equatable {
  const OrderSearchState();

  String? get orderId;

  @override
  List<Object?> get props => [orderId];
}

final class OrderSearchIdle extends OrderSearchState {
  const OrderSearchIdle();

  @override
  String? get orderId => null;
}

final class OrderSearchLoading extends OrderSearchState {
  const OrderSearchLoading(this.orderId);

  @override
  final String orderId;
}

final class OrderSearchSuccess extends OrderSearchState {
  const OrderSearchSuccess(this.order);

  final Order order;

  @override
  String get orderId => order.orderId;

  @override
  List<Object?> get props => [order];
}

final class OrderSearchNotFound extends OrderSearchState {
  const OrderSearchNotFound(this.orderId);

  @override
  final String orderId;
}

final class OrderSearchFailure extends OrderSearchState {
  const OrderSearchFailure(this.orderId, this.failure);

  @override
  final String orderId;
  final AppFailure failure;

  @override
  List<Object?> get props => [orderId, failure];
}
