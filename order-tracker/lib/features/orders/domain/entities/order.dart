import 'package:equatable/equatable.dart';
import 'package:order_tracker/features/orders/domain/entities/order_line.dart';
import 'package:order_tracker/features/orders/domain/entities/order_status.dart';

final class Order extends Equatable {
  const Order({
    required this.orderId,
    required this.status,
    required this.market,
    required this.currency,
    required this.client,
    required this.lines,
    required this.totals,
    required this.receivedAt,
    required this.processedAt,
    this.channel,
    this.reason,
    this.violations = const [],
    this.failure,
    this.occurredAt,
    this.traceId,
  });

  final String orderId;
  final OrderStatus status;
  final String market;
  final String currency;
  final String? channel;
  final OrderClient client;
  final List<OrderLine> lines;
  final OrderTotals totals;
  final String? reason;
  final List<Violation> violations;
  final ProcessingFailure? failure;
  final DateTime? occurredAt;
  final DateTime receivedAt;
  final DateTime processedAt;
  final String? traceId;

  bool get isRejected => status == OrderStatus.rejected;

  bool get isTechnicalFailure => status == OrderStatus.technicalFailure;

  @override
  List<Object?> get props => [
    orderId,
    status,
    market,
    currency,
    channel,
    client,
    lines,
    totals,
    reason,
    violations,
    failure,
    occurredAt,
    receivedAt,
    processedAt,
    traceId,
  ];
}

final class OrderClient extends Equatable {
  const OrderClient({
    required this.clientId,
    this.name,
    this.status,
    this.segment,
    this.taxRegime,
    this.market,
  });

  final String clientId;
  final String? name;
  final String? status;
  final String? segment;
  final String? taxRegime;
  final String? market;

  @override
  List<Object?> get props => [clientId, name, status, segment, taxRegime, market];
}

final class OrderTotals extends Equatable {
  const OrderTotals({
    required this.grossSubtotal,
    required this.discount,
    required this.netSubtotal,
    required this.tax,
    required this.grandTotal,
  });

  final double grossSubtotal;
  final double discount;
  final double netSubtotal;
  final double tax;
  final double grandTotal;

  @override
  List<Object?> get props => [grossSubtotal, discount, netSubtotal, tax, grandTotal];
}

final class Violation extends Equatable {
  const Violation({required this.code, required this.message, this.productId});

  final String code;
  final String message;
  final String? productId;

  @override
  List<Object?> get props => [code, message, productId];
}

final class ProcessingFailure extends Equatable {
  const ProcessingFailure({required this.category, required this.cause, required this.attempts});

  final String category;
  final String cause;
  final int attempts;

  @override
  List<Object?> get props => [category, cause, attempts];
}
