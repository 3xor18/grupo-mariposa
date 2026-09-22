import 'package:order_tracker/features/orders/data/dto/order_dto.dart';
import 'package:order_tracker/features/orders/data/dto/order_page_dto.dart';
import 'package:order_tracker/features/orders/data/mappers/order_codes.dart';
import 'package:order_tracker/features/orders/domain/entities/order.dart';
import 'package:order_tracker/features/orders/domain/entities/order_line.dart';
import 'package:order_tracker/features/orders/domain/entities/order_summary.dart';

extension OrderDtoMapper on OrderDto {
  Order toDomain() {
    return Order(
      orderId: orderId,
      status: OrderStatusCodes.toDomain(status),
      market: market,
      currency: currency,
      channel: channel,
      client: client.toDomain(),
      lines: lines.map((line) => line.toDomain()).toList(growable: false),
      totals: totals.toDomain(),
      reason: reason,
      violations: violations.map((violation) => violation.toDomain()).toList(growable: false),
      failure: failure?.toDomain(),
      occurredAt: occurredAt,
      receivedAt: receivedAt,
      processedAt: processedAt,
      traceId: traceId,
    );
  }
}

extension ClientSnapshotDtoMapper on ClientSnapshotDto {
  OrderClient toDomain() {
    return OrderClient(
      clientId: clientId,
      name: name,
      status: status,
      segment: segment,
      taxRegime: taxRegime,
      market: market,
    );
  }
}

extension OrderLineDtoMapper on OrderLineDto {
  OrderLine toDomain() {
    return OrderLine(
      productId: productId,
      name: name,
      sku: sku,
      taxCategory: taxCategory,
      quantity: quantity,
      unitPrice: unitPrice,
      grossSubtotal: grossSubtotal,
      discountRate: discountRate,
      discount: discount,
      netSubtotal: netSubtotal,
      taxRate: taxRate,
      taxAmount: taxAmount,
      lineTotal: lineTotal,
    );
  }
}

extension TotalsDtoMapper on TotalsDto {
  OrderTotals toDomain() {
    return OrderTotals(
      grossSubtotal: grossSubtotal,
      discount: discount,
      netSubtotal: netSubtotal,
      tax: tax,
      grandTotal: grandTotal,
    );
  }
}

extension ViolationDtoMapper on ViolationDto {
  Violation toDomain() => Violation(code: code, message: message, productId: productId);
}

extension FailureDtoMapper on FailureDto {
  ProcessingFailure toDomain() {
    return ProcessingFailure(category: category, cause: cause, attempts: attempts);
  }
}

extension OrderSummaryDtoMapper on OrderSummaryDto {
  OrderSummary toDomain() {
    return OrderSummary(
      orderId: orderId,
      status: OrderStatusCodes.toDomain(status),
      market: market,
      currency: currency,
      clientId: clientId,
      grandTotal: grandTotal,
      reason: reason,
      processedAt: processedAt,
    );
  }
}

extension OrderPageDtoMapper on OrderPageDto {
  OrderPage toDomain() {
    return OrderPage(
      items: items.map((item) => item.toDomain()).toList(growable: false),
      page: page,
      size: size,
      totalElements: totalElements,
      totalPages: totalPages,
    );
  }
}
