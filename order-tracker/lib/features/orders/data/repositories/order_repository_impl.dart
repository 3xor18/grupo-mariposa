import 'package:order_tracker/core/result/result.dart';
import 'package:order_tracker/features/orders/data/datasources/orders_api.dart';
import 'package:order_tracker/features/orders/data/mappers/order_codes.dart';
import 'package:order_tracker/features/orders/data/mappers/order_mapper.dart';
import 'package:order_tracker/features/orders/domain/entities/order.dart';
import 'package:order_tracker/features/orders/domain/entities/order_summary.dart';
import 'package:order_tracker/features/orders/domain/entities/orders_filter.dart';
import 'package:order_tracker/features/orders/domain/repositories/order_repository.dart';

final class OrderRepositoryImpl implements OrderRepository {
  const OrderRepositoryImpl(this._api);

  final OrdersApi _api;

  @override
  Future<Result<Order>> findById(String orderId) async {
    final result = await _api.getOrder(orderId);
    return result.map((dto) => dto.toDomain());
  }

  @override
  Future<Result<OrderPage>> list({
    required OrdersFilter filter,
    required int page,
    required int size,
  }) async {
    final status = filter.status;
    final market = filter.market;
    final result = await _api.listOrders(
      page: page,
      size: size,
      status: status == null ? null : OrderStatusCodes.toCode(status),
      market: market == null ? null : MarketCodes.toCode(market),
    );
    return result.map((dto) => dto.toDomain());
  }
}
