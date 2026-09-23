import 'package:order_tracker/core/json/json_map.dart';

abstract final class _SummaryFields {
  static const orderId = 'orderId';
  static const status = 'status';
  static const market = 'market';
  static const currency = 'currency';
  static const clientId = 'clientId';
  static const eventVersion = 'eventVersion';
  static const grandTotal = 'grandTotal';
  static const reason = 'reason';
  static const processedAt = 'processedAt';
}

final class OrderSummaryDto {
  const OrderSummaryDto({
    required this.orderId,
    required this.status,
    required this.market,
    required this.currency,
    required this.clientId,
    required this.grandTotal,
    required this.processedAt,
    this.eventVersion,
    this.reason,
  });

  factory OrderSummaryDto.fromJson(JsonMap json) {
    return OrderSummaryDto(
      orderId: json.requireString(_SummaryFields.orderId),
      status: json.requireString(_SummaryFields.status),
      market: json.requireString(_SummaryFields.market),
      currency: json.requireString(_SummaryFields.currency),
      clientId: json.requireString(_SummaryFields.clientId),
      eventVersion: json.optionalInt(_SummaryFields.eventVersion),
      grandTotal: json.requireDecimal(_SummaryFields.grandTotal),
      reason: json.optionalString(_SummaryFields.reason),
      processedAt: json.requireDateTime(_SummaryFields.processedAt),
    );
  }

  final String orderId;
  final String status;
  final String market;
  final String currency;
  final String clientId;
  final int? eventVersion;
  final String grandTotal;
  final String? reason;
  final DateTime processedAt;
}

abstract final class _PageFields {
  static const items = 'items';
  static const page = 'page';
  static const size = 'size';
  static const totalElements = 'totalElements';
  static const totalPages = 'totalPages';
}

final class OrderPageDto {
  const OrderPageDto({
    required this.items,
    required this.page,
    required this.size,
    required this.totalElements,
    required this.totalPages,
  });

  factory OrderPageDto.fromJson(JsonMap json) {
    return OrderPageDto(
      items: json.requireObjectList(_PageFields.items).map(OrderSummaryDto.fromJson).toList(),
      page: json.requireInt(_PageFields.page),
      size: json.requireInt(_PageFields.size),
      totalElements: json.requireInt(_PageFields.totalElements),
      totalPages: json.requireInt(_PageFields.totalPages),
    );
  }

  final List<OrderSummaryDto> items;
  final int page;
  final int size;
  final int totalElements;
  final int totalPages;
}
