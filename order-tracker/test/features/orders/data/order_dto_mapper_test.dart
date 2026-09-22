import 'package:flutter_test/flutter_test.dart';
import 'package:order_tracker/core/json/json_map.dart';
import 'package:order_tracker/core/money/money.dart';
import 'package:order_tracker/features/orders/data/dto/order_dto.dart';
import 'package:order_tracker/features/orders/data/dto/order_page_dto.dart';
import 'package:order_tracker/features/orders/data/mappers/order_codes.dart';
import 'package:order_tracker/features/orders/data/mappers/order_mapper.dart';
import 'package:order_tracker/features/orders/domain/entities/market.dart';
import 'package:order_tracker/features/orders/domain/entities/order_status.dart';

import '../../../fixtures/order_fixtures.dart';

void main() {
  group('OrderDto', () {
    test('should parse and map a complete approved order', () {
      final order = OrderDto.fromJson(JsonMap(approvedOrderJson())).toDomain();
      expect(order, approvedOrder());
    });

    test('should parse an order without optional members', () {
      final json = minimalOrderJson();
      final dto = OrderDto.fromJson(JsonMap(json));
      expect(dto.channel, isNull);
      expect(dto.failure, isNull);
      expect(dto.occurredAt, isNull);
      expect(dto.client.name, isNull);
      expect(dto.lines.single.lineTotal, isNull);
      expect(dto.toDomain(), rejectedOrder());
    });

    test('should parse and map technical failure details', () {
      final json = minimalOrderJson()
        ..['status'] = 'TECHNICAL_FAILURE'
        ..['failure'] = {'category': 'DEPENDENCY_UNAVAILABLE', 'cause': 'timeout', 'attempts': 5};
      final order = OrderDto.fromJson(JsonMap(json)).toDomain();
      expect(order.isTechnicalFailure, isTrue);
      expect(order.failure?.attempts, 5);
      expect(order.failure?.category, 'DEPENDENCY_UNAVAILABLE');
    });

    test('should map statuses unknown to this client version to unknown', () {
      final json = approvedOrderJson()..['status'] = 'ON_HOLD';
      expect(OrderDto.fromJson(JsonMap(json)).toDomain().status, OrderStatus.unknown);
    });

    test('should parse amounts into money of the order currency', () {
      final order = OrderDto.fromJson(JsonMap(approvedOrderJson())).toDomain();
      expect(order.totals.grandTotal, const Money(minorUnits: 210011, currency: 'MXN'));
      expect(order.lines.last.unitPrice.minorUnits, 8200);
      expect(order.eventVersion, 1);
      expect(order.market, Market.mx);
    });

    test('should reject amounts with more than two decimals when mapping', () {
      final json = approvedOrderJson();
      (json['totals']! as Map<String, Object?>)['grandTotal'] = 2100.111;
      final dto = OrderDto.fromJson(JsonMap(json));
      expect(dto.toDomain, throwsFormatException);
    });

    test('should reject documents missing required members', () {
      final missingTotals = approvedOrderJson()..remove('totals');
      final wrongType = approvedOrderJson()..['eventVersion'] = 'one';
      final missingLines = approvedOrderJson()..remove('lines');
      expect(() => OrderDto.fromJson(JsonMap(missingTotals)), throwsFormatException);
      expect(() => OrderDto.fromJson(JsonMap(wrongType)), throwsFormatException);
      expect(() => OrderDto.fromJson(JsonMap(missingLines)), throwsFormatException);
    });
  });

  group('OrderPageDto', () {
    test('should parse and map a page of summaries', () {
      final page = OrderPageDto.fromJson(JsonMap(orderPageJson())).toDomain();
      expect(page, orderPage(items: [summary(approvedOrderId)], totalPages: 2, totalElements: 21));
      expect(page.hasMore, isTrue);
    });

    test('should parse summaries without optional members', () {
      final json = orderPageJson(page: 1);
      final items = json['items']! as List<Object?>;
      (items.single! as Map<String, Object?>)
        ..remove('eventVersion')
        ..remove('reason');
      final dto = OrderPageDto.fromJson(JsonMap(json));
      expect(dto.items.single.eventVersion, isNull);
      expect(dto.toDomain().hasMore, isFalse);
    });
  });

  group('codes', () {
    test('should translate domain filters to contract codes', () {
      expect(OrderStatusCodes.toCode(OrderStatus.approved), 'APPROVED');
      expect(OrderStatusCodes.toCode(OrderStatus.rejected), 'REJECTED');
      expect(OrderStatusCodes.toCode(OrderStatus.technicalFailure), 'TECHNICAL_FAILURE');
      expect(OrderStatusCodes.toCode(OrderStatus.unknown), isNull);
      expect(Market.values.map(MarketCodes.toCode), ['MX', 'CO', 'PE', null]);
      expect(MarketCodes.toDomain('BR'), Market.unknown);
      expect(MarketCodes.toDomain('PE'), Market.pe);
    });
  });
}
