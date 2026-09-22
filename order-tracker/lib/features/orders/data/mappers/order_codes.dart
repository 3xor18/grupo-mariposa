import 'package:order_tracker/features/orders/domain/entities/market.dart';
import 'package:order_tracker/features/orders/domain/entities/order_status.dart';

abstract final class OrderStatusCodes {
  static const Map<String, OrderStatus> _byCode = {
    'APPROVED': OrderStatus.approved,
    'REJECTED': OrderStatus.rejected,
    'TECHNICAL_FAILURE': OrderStatus.technicalFailure,
  };

  static OrderStatus toDomain(String code) => _byCode[code] ?? OrderStatus.unknown;

  static String? toCode(OrderStatus status) => _codeOf(_byCode, status);
}

abstract final class MarketCodes {
  static const Map<String, Market> _byCode = {
    'MX': Market.mx,
    'CO': Market.co,
    'PE': Market.pe,
  };

  static Market toDomain(String code) => _byCode[code] ?? Market.unknown;

  static String? toCode(Market market) => _codeOf(_byCode, market);
}

String? _codeOf<T>(Map<String, T> codes, T value) {
  for (final MapEntry(:key, value: candidate) in codes.entries) {
    if (candidate == value) {
      return key;
    }
  }
  return null;
}
