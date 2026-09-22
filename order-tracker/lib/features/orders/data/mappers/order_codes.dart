import 'package:order_tracker/features/orders/domain/entities/market.dart';
import 'package:order_tracker/features/orders/domain/entities/order_status.dart';

abstract final class OrderStatusCodes {
  static const Map<String, OrderStatus> _byCode = {
    'APPROVED': OrderStatus.approved,
    'REJECTED': OrderStatus.rejected,
    'TECHNICAL_FAILURE': OrderStatus.technicalFailure,
  };

  static OrderStatus toDomain(String code) => _byCode[code] ?? OrderStatus.unknown;

  static String? toCode(OrderStatus status) {
    for (final entry in _byCode.entries) {
      if (entry.value == status) {
        return entry.key;
      }
    }
    return null;
  }
}

abstract final class MarketCodes {
  static const Map<Market, String> _codeByMarket = {
    Market.mx: 'MX',
    Market.co: 'CO',
    Market.pe: 'PE',
  };

  static String toCode(Market market) => _codeByMarket[market]!;
}
