import 'package:order_tracker/core/error/app_failure.dart';
import 'package:order_tracker/core/http/api_client.dart';
import 'package:order_tracker/core/json/json_map.dart';
import 'package:order_tracker/core/result/result.dart';
import 'package:order_tracker/features/orders/data/dto/order_dto.dart';
import 'package:order_tracker/features/orders/data/dto/order_page_dto.dart';

abstract final class OrdersQueryParameters {
  static const status = 'status';
  static const market = 'market';
  static const page = 'page';
  static const size = 'size';
}

final class OrdersApi {
  const OrdersApi(this._client);

  static const _ordersPath = 'orders';

  final ApiClient _client;

  Future<Result<OrderDto>> getOrder(String orderId) async {
    final response = await _client.getJson([_ordersPath, orderId]);
    return _parse(response, OrderDto.fromJson);
  }

  Future<Result<OrderPageDto>> listOrders({
    required int page,
    required int size,
    String? status,
    String? market,
  }) async {
    final query = {
      OrdersQueryParameters.status: ?status,
      OrdersQueryParameters.market: ?market,
      OrdersQueryParameters.page: '$page',
      OrdersQueryParameters.size: '$size',
    };
    final response = await _client.getJson([_ordersPath], query: query);
    return _parse(response, OrderPageDto.fromJson);
  }

  Result<T> _parse<T>(Result<Object?> response, T Function(JsonMap json) fromJson) {
    return switch (response) {
      Ok<Object?>(:final value) => _decode(value, fromJson),
      Err<Object?>(:final failure) => Result.err(failure),
    };
  }

  Result<T> _decode<T>(Object? body, T Function(JsonMap json) fromJson) {
    try {
      return Result.ok(fromJson(JsonMap.parse(body)));
    } on FormatException {
      return const Result.err(UnexpectedResponseFailure());
    }
  }
}
