import 'package:order_tracker/core/money/money.dart';
import 'package:order_tracker/core/money/unit_price.dart';
import 'package:order_tracker/features/orders/data/dto/order_dto.dart';
import 'package:order_tracker/features/orders/data/dto/order_page_dto.dart';
import 'package:order_tracker/features/orders/data/mappers/order_codes.dart';
import 'package:order_tracker/features/orders/domain/entities/order.dart';
import 'package:order_tracker/features/orders/domain/entities/order_line.dart';
import 'package:order_tracker/features/orders/domain/entities/order_page.dart';
import 'package:order_tracker/features/orders/domain/entities/order_summary.dart';

extension OrderDtoMapper on OrderDto {
  Order toDomain() {
    return Order(
      orderId: orderId,
      eventVersion: eventVersion,
      status: OrderStatusCodes.toDomain(status),
      market: MarketCodes.toDomain(market),
      currency: currency,
      channel: channel,
      client: client.toDomain(),
      lines: lines.map((line) => line.toDomain(currency)).toList(growable: false),
      totals: totals.toDomain(currency),
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
  OrderLine toDomain(String currency) {
    Money? money(String? amount) => amount == null ? null : Money.parse(amount, currency: currency);
    return OrderLine(
      productId: productId,
      name: name,
      sku: sku,
      taxCategory: taxCategory,
      quantity: quantity,
      unitPrice: UnitPrice.parse(unitPrice, currency: currency),
      grossSubtotal: money(grossSubtotal),
      discountRate: discountRate,
      discount: money(discount),
      netSubtotal: money(netSubtotal),
      taxRate: taxRate,
      taxAmount: money(taxAmount),
      lineTotal: money(lineTotal),
    );
  }
}

extension TotalsDtoMapper on TotalsDto {
  OrderTotals toDomain(String currency) {
    Money money(String amount) => Money.parse(amount, currency: currency);
    return OrderTotals(
      grossSubtotal: money(grossSubtotal),
      discount: money(discount),
      netSubtotal: money(netSubtotal),
      tax: money(tax),
      grandTotal: money(grandTotal),
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
      market: MarketCodes.toDomain(market),
      clientId: clientId,
      eventVersion: eventVersion,
      grandTotal: Money.parse(grandTotal, currency: currency),
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
