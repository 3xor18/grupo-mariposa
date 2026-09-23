import 'package:order_tracker/features/orders/domain/entities/order_status.dart';

abstract final class OrderStatusCodes {
  static const Map<String, OrderStatus> _byCode = {
    'APPROVED': OrderStatus.approved,
    'REJECTED': OrderStatus.rejected,
    'TECHNICAL_FAILURE': OrderStatus.technicalFailure,
  };

  static OrderStatus toDomain(String code) => _byCode[code] ?? OrderStatus.unknown;

  static String? toCode(OrderStatus status) {
    for (final MapEntry(:key, :value) in _byCode.entries) {
      if (value == status) {
        return key;
      }
    }
    return null;
  }
}
