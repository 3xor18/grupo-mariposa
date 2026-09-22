import 'package:order_tracker/core/result/result.dart';
import 'package:order_tracker/features/orders/domain/entities/order.dart';
import 'package:order_tracker/features/orders/domain/entities/order_summary.dart';
import 'package:order_tracker/features/orders/domain/entities/orders_filter.dart';

abstract interface class OrderRepository {
  Future<Result<Order>> findById(String orderId);

  Future<Result<OrderPage>> list({
    required OrdersFilter filter,
    required int page,
    required int size,
  });
}
