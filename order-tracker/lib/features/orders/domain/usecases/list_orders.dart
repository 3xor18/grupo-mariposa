import 'package:order_tracker/core/result/result.dart';
import 'package:order_tracker/features/orders/domain/entities/order_summary.dart';
import 'package:order_tracker/features/orders/domain/entities/orders_filter.dart';
import 'package:order_tracker/features/orders/domain/repositories/order_repository.dart';

final class ListOrders {
  const ListOrders(this._repository);

  final OrderRepository _repository;

  Future<Result<OrderPage>> call({required OrdersFilter filter, required int page}) {
    return _repository.list(filter: filter, page: page, size: OrdersFilter.pageSize);
  }
}
