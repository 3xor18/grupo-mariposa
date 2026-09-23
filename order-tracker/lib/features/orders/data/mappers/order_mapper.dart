import 'package:order_tracker/core/markets/market_catalog.dart';
import 'package:order_tracker/core/money/money.dart';
import 'package:order_tracker/core/money/unit_price.dart';
import 'package:order_tracker/features/orders/data/dto/order_dto.dart';
import 'package:order_tracker/features/orders/data/dto/order_page_dto.dart';
import 'package:order_tracker/features/orders/data/mappers/order_codes.dart';
import 'package:order_tracker/features/orders/domain/entities/market_code.dart';
import 'package:order_tracker/features/orders/domain/entities/order.dart';
import 'package:order_tracker/features/orders/domain/entities/order_line.dart';
import 'package:order_tracker/features/orders/domain/entities/order_page.dart';
import 'package:order_tracker/features/orders/domain/entities/order_summary.dart';

extension OrderDtoMapper on OrderDto {
  Order toDomain(MarketCatalog catalog) {
    final digits = catalog.fractionDigitsOf(currency);
    return Order(
      orderId: orderId,
      eventVersion: eventVersion,
      status: OrderStatusCodes.toDomain(status),
      market: MarketCode(market),
      currency: currency,
      channel: channel,
      client: client.toDomain(),
      lines: lines.map((line) => line.toDomain(currency, digits)).toList(growable: false),
      totals: totals.toDomain(currency, digits),
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
  OrderLine toDomain(String currency, int digits) {
    Money? money(String? amount) {
      return amount == null
          ? null
          : Money.parse(amount, currency: currency, fractionDigits: digits);
    }

    return OrderLine(
      productId: productId,
      name: name,
      sku: sku,
      taxCategory: taxCategory,
      quantity: quantity,
      unitPrice: UnitPrice.parse(unitPrice, currency: currency, currencyDigits: digits),
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
  OrderTotals toDomain(String currency, int digits) {
    Money money(String amount) {
      return Money.parse(amount, currency: currency, fractionDigits: digits);
    }

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
  OrderSummary toDomain(MarketCatalog catalog) {
    return OrderSummary(
      orderId: orderId,
      status: OrderStatusCodes.toDomain(status),
      market: MarketCode(market),
      clientId: clientId,
      eventVersion: eventVersion,
      grandTotal: Money.parse(
        grandTotal,
        currency: currency,
        fractionDigits: catalog.fractionDigitsOf(currency),
      ),
      reason: reason,
      processedAt: processedAt,
    );
  }
}

extension OrderPageDtoMapper on OrderPageDto {
  OrderPage toDomain(MarketCatalog catalog) {
    return OrderPage(
      items: items.map((item) => item.toDomain(catalog)).toList(growable: false),
      page: page,
      size: size,
      totalElements: totalElements,
      totalPages: totalPages,
    );
  }
}
