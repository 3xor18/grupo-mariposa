import 'package:equatable/equatable.dart';
import 'package:order_tracker/features/orders/domain/entities/market_code.dart';
import 'package:order_tracker/features/orders/domain/entities/order_status.dart';

sealed class OrdersListEvent extends Equatable {
  const OrdersListEvent();

  @override
  List<Object?> get props => [];
}

sealed class OrdersListQueryEvent extends OrdersListEvent {
  const OrdersListQueryEvent();
}

final class OrdersListRequested extends OrdersListQueryEvent {
  const OrdersListRequested();
}

final class OrdersListRefreshed extends OrdersListQueryEvent {
  const OrdersListRefreshed();
}

final class OrdersListStatusToggled extends OrdersListQueryEvent {
  const OrdersListStatusToggled(this.status);

  final OrderStatus status;

  @override
  List<Object?> get props => [status];
}

final class OrdersListMarketToggled extends OrdersListQueryEvent {
  const OrdersListMarketToggled(this.market);

  final MarketCode market;

  @override
  List<Object?> get props => [market];
}

final class OrdersListNextPageRequested extends OrdersListEvent {
  const OrdersListNextPageRequested();
}
