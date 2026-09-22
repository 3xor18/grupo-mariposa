import 'package:equatable/equatable.dart';
import 'package:order_tracker/features/orders/domain/entities/order_status.dart';

final class OrderSummary extends Equatable {
  const OrderSummary({
    required this.orderId,
    required this.status,
    required this.market,
    required this.currency,
    required this.clientId,
    required this.grandTotal,
    required this.processedAt,
    this.reason,
  });

  final String orderId;
  final OrderStatus status;
  final String market;
  final String currency;
  final String clientId;
  final double grandTotal;
  final String? reason;
  final DateTime processedAt;

  @override
  List<Object?> get props => [
    orderId,
    status,
    market,
    currency,
    clientId,
    grandTotal,
    reason,
    processedAt,
  ];
}

final class OrderPage extends Equatable {
  const OrderPage({
    required this.items,
    required this.page,
    required this.size,
    required this.totalElements,
    required this.totalPages,
  });

  final List<OrderSummary> items;
  final int page;
  final int size;
  final int totalElements;
  final int totalPages;

  bool get hasMore => page + 1 < totalPages;

  @override
  List<Object?> get props => [items, page, size, totalElements, totalPages];
}
