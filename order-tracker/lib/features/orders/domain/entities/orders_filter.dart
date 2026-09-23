import 'package:equatable/equatable.dart';
import 'package:order_tracker/features/orders/domain/entities/market.dart';
import 'package:order_tracker/features/orders/domain/entities/order_status.dart';

final class OrdersFilter extends Equatable {
  const OrdersFilter({this.status, this.market});

  static const pageSize = 20;
  static const firstPage = 0;

  final OrderStatus? status;
  final Market? market;

  OrdersFilter withStatus(OrderStatus? value) => OrdersFilter(status: value, market: market);

  OrdersFilter withMarket(Market? value) => OrdersFilter(status: status, market: value);

  @override
  List<Object?> get props => [status, market];
}
