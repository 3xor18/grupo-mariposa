import 'package:order_tracker/core/error/app_failure.dart';
import 'package:order_tracker/core/result/result.dart';
import 'package:order_tracker/features/orders/data/datasources/orders_api.dart';
import 'package:order_tracker/features/orders/data/mappers/order_codes.dart';
import 'package:order_tracker/features/orders/data/mappers/order_mapper.dart';
import 'package:order_tracker/features/orders/domain/entities/order.dart';
import 'package:order_tracker/features/orders/domain/entities/order_page.dart';
import 'package:order_tracker/features/orders/domain/entities/orders_filter.dart';
import 'package:order_tracker/features/orders/domain/repositories/order_repository.dart';

final class OrderRepositoryImpl implements OrderRepository {
  const OrderRepositoryImpl(this._api);

  final OrdersApi _api;

  @override
  Future<Result<Order>> findById(String orderId) async {
    return _toDomain(await _api.getOrder(orderId), (dto) => dto.toDomain());
  }

  @override
  Future<Result<OrderPage>> list({
    required OrdersFilter filter,
    required int page,
    required int size,
  }) async {
    final result = await _api.listOrders(
      page: page,
      size: size,
      status: switch (filter.status) {
        final status? => OrderStatusCodes.toCode(status),
        null => null,
      },
      market: switch (filter.market) {
        final market? => MarketCodes.toCode(market),
        null => null,
      },
    );
    return _toDomain(result, (dto) => dto.toDomain());
  }

  Result<T> _toDomain<D, T>(Result<D> result, T Function(D dto) map) {
    try {
      return result.map(map);
    } on FormatException {
      return Result<T>.err(const UnexpectedResponseFailure());
    }
  }
}
