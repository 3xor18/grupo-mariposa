import 'package:order_tracker/core/json/json_map.dart';

abstract final class _OrderFields {
  static const orderId = 'orderId';
  static const sourceEventId = 'sourceEventId';
  static const eventVersion = 'eventVersion';
  static const status = 'status';
  static const market = 'market';
  static const currency = 'currency';
  static const channel = 'channel';
  static const client = 'client';
  static const lines = 'lines';
  static const totals = 'totals';
  static const reason = 'reason';
  static const violations = 'violations';
  static const failure = 'failure';
  static const occurredAt = 'occurredAt';
  static const receivedAt = 'receivedAt';
  static const processedAt = 'processedAt';
  static const traceId = 'traceId';
}

final class OrderDto {
  const OrderDto({
    required this.orderId,
    required this.sourceEventId,
    required this.eventVersion,
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

  factory OrderDto.fromJson(JsonMap json) {
    return OrderDto(
      orderId: json.requireString(_OrderFields.orderId),
      sourceEventId: json.requireString(_OrderFields.sourceEventId),
      eventVersion: json.requireInt(_OrderFields.eventVersion),
      status: json.requireString(_OrderFields.status),
      market: json.requireString(_OrderFields.market),
      currency: json.requireString(_OrderFields.currency),
      channel: json.optionalString(_OrderFields.channel),
      client: ClientSnapshotDto.fromJson(json.requireObject(_OrderFields.client)),
      lines: json.requireObjectList(_OrderFields.lines).map(OrderLineDto.fromJson).toList(),
      totals: TotalsDto.fromJson(json.requireObject(_OrderFields.totals)),
      reason: json.optionalString(_OrderFields.reason),
      violations: json.objectList(_OrderFields.violations).map(ViolationDto.fromJson).toList(),
      failure: _failureOf(json.optionalObject(_OrderFields.failure)),
      occurredAt: json.optionalDateTime(_OrderFields.occurredAt),
      receivedAt: json.requireDateTime(_OrderFields.receivedAt),
      processedAt: json.requireDateTime(_OrderFields.processedAt),
      traceId: json.optionalString(_OrderFields.traceId),
    );
  }

  final String orderId;
  final String sourceEventId;
  final int eventVersion;
  final String status;
  final String market;
  final String currency;
  final String? channel;
  final ClientSnapshotDto client;
  final List<OrderLineDto> lines;
  final TotalsDto totals;
  final String? reason;
  final List<ViolationDto> violations;
  final FailureDto? failure;
  final DateTime? occurredAt;
  final DateTime receivedAt;
  final DateTime processedAt;
  final String? traceId;

  static FailureDto? _failureOf(JsonMap? json) => json == null ? null : FailureDto.fromJson(json);
}

abstract final class _ClientFields {
  static const clientId = 'clientId';
  static const name = 'name';
  static const status = 'status';
  static const segment = 'segment';
  static const taxRegime = 'taxRegime';
  static const market = 'market';
}

final class ClientSnapshotDto {
  const ClientSnapshotDto({
    required this.clientId,
    this.name,
    this.status,
    this.segment,
    this.taxRegime,
    this.market,
  });

  factory ClientSnapshotDto.fromJson(JsonMap json) {
    return ClientSnapshotDto(
      clientId: json.requireString(_ClientFields.clientId),
      name: json.optionalString(_ClientFields.name),
      status: json.optionalString(_ClientFields.status),
      segment: json.optionalString(_ClientFields.segment),
      taxRegime: json.optionalString(_ClientFields.taxRegime),
      market: json.optionalString(_ClientFields.market),
    );
  }

  final String clientId;
  final String? name;
  final String? status;
  final String? segment;
  final String? taxRegime;
  final String? market;
}

abstract final class _LineFields {
  static const productId = 'productId';
  static const name = 'name';
  static const sku = 'sku';
  static const taxCategory = 'taxCategory';
  static const quantity = 'quantity';
  static const unitPrice = 'unitPrice';
  static const grossSubtotal = 'grossSubtotal';
  static const discountRate = 'discountRate';
  static const discount = 'discount';
  static const netSubtotal = 'netSubtotal';
  static const taxRate = 'taxRate';
  static const taxAmount = 'taxAmount';
  static const lineTotal = 'lineTotal';
}

final class OrderLineDto {
  const OrderLineDto({
    required this.productId,
    required this.quantity,
    required this.unitPrice,
    this.name,
    this.sku,
    this.taxCategory,
    this.grossSubtotal,
    this.discountRate,
    this.discount,
    this.netSubtotal,
    this.taxRate,
    this.taxAmount,
    this.lineTotal,
  });

  factory OrderLineDto.fromJson(JsonMap json) {
    return OrderLineDto(
      productId: json.requireString(_LineFields.productId),
      name: json.optionalString(_LineFields.name),
      sku: json.optionalString(_LineFields.sku),
      taxCategory: json.optionalString(_LineFields.taxCategory),
      quantity: json.requireInt(_LineFields.quantity),
      unitPrice: json.requireDouble(_LineFields.unitPrice),
      grossSubtotal: json.optionalDouble(_LineFields.grossSubtotal),
      discountRate: json.optionalDouble(_LineFields.discountRate),
      discount: json.optionalDouble(_LineFields.discount),
      netSubtotal: json.optionalDouble(_LineFields.netSubtotal),
      taxRate: json.optionalDouble(_LineFields.taxRate),
      taxAmount: json.optionalDouble(_LineFields.taxAmount),
      lineTotal: json.optionalDouble(_LineFields.lineTotal),
    );
  }

  final String productId;
  final String? name;
  final String? sku;
  final String? taxCategory;
  final int quantity;
  final double unitPrice;
  final double? grossSubtotal;
  final double? discountRate;
  final double? discount;
  final double? netSubtotal;
  final double? taxRate;
  final double? taxAmount;
  final double? lineTotal;
}

abstract final class _TotalsFields {
  static const grossSubtotal = 'grossSubtotal';
  static const discount = 'discount';
  static const netSubtotal = 'netSubtotal';
  static const tax = 'tax';
  static const grandTotal = 'grandTotal';
}

final class TotalsDto {
  const TotalsDto({
    required this.grossSubtotal,
    required this.discount,
    required this.netSubtotal,
    required this.tax,
    required this.grandTotal,
  });

  factory TotalsDto.fromJson(JsonMap json) {
    return TotalsDto(
      grossSubtotal: json.requireDouble(_TotalsFields.grossSubtotal),
      discount: json.requireDouble(_TotalsFields.discount),
      netSubtotal: json.requireDouble(_TotalsFields.netSubtotal),
      tax: json.requireDouble(_TotalsFields.tax),
      grandTotal: json.requireDouble(_TotalsFields.grandTotal),
    );
  }

  final double grossSubtotal;
  final double discount;
  final double netSubtotal;
  final double tax;
  final double grandTotal;
}

abstract final class _ViolationFields {
  static const code = 'code';
  static const message = 'message';
  static const productId = 'productId';
}

final class ViolationDto {
  const ViolationDto({required this.code, required this.message, this.productId});

  factory ViolationDto.fromJson(JsonMap json) {
    return ViolationDto(
      code: json.requireString(_ViolationFields.code),
      message: json.requireString(_ViolationFields.message),
      productId: json.optionalString(_ViolationFields.productId),
    );
  }

  final String code;
  final String message;
  final String? productId;
}

abstract final class _FailureFields {
  static const category = 'category';
  static const cause = 'cause';
  static const attempts = 'attempts';
}

final class FailureDto {
  const FailureDto({required this.category, required this.cause, required this.attempts});

  factory FailureDto.fromJson(JsonMap json) {
    return FailureDto(
      category: json.requireString(_FailureFields.category),
      cause: json.requireString(_FailureFields.cause),
      attempts: json.requireInt(_FailureFields.attempts),
    );
  }

  final String category;
  final String cause;
  final int attempts;
}
