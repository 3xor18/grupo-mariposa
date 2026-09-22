import 'package:order_tracker/core/error/app_failure.dart';
import 'package:order_tracker/core/result/result.dart';
import 'package:order_tracker/features/orders/domain/entities/order.dart';
import 'package:order_tracker/features/orders/domain/entities/order_id.dart';
import 'package:order_tracker/features/orders/domain/repositories/order_repository.dart';

final class SearchOrder {
  const SearchOrder(this._repository);

  final OrderRepository _repository;

  Future<Result<Order>> call(String rawOrderId) async {
    if (OrderId.validate(rawOrderId) != null) {
      return const Result.err(ValidationFailure());
    }
    return _repository.findById(OrderId.normalize(rawOrderId));
  }
}
