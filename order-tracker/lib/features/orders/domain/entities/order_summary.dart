import 'package:equatable/equatable.dart';
import 'package:order_tracker/core/money/money.dart';
import 'package:order_tracker/features/orders/domain/entities/market_code.dart';
import 'package:order_tracker/features/orders/domain/entities/order_status.dart';

final class OrderSummary extends Equatable {
  const OrderSummary({
    required this.orderId,
    required this.status,
    required this.market,
    required this.clientId,
    required this.grandTotal,
    required this.processedAt,
    this.eventVersion,
    this.reason,
  });

  final String orderId;
  final OrderStatus status;
  final MarketCode market;
  final String clientId;
  final int? eventVersion;
  final Money grandTotal;
  final String? reason;
  final DateTime processedAt;

  @override
  List<Object?> get props => [
    orderId,
    status,
    market,
    clientId,
    eventVersion,
    grandTotal,
    reason,
    processedAt,
  ];
}
