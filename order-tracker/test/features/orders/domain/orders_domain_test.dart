import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:order_tracker/core/error/app_failure.dart';
import 'package:order_tracker/core/result/result.dart';
import 'package:order_tracker/features/orders/domain/entities/market.dart';
import 'package:order_tracker/features/orders/domain/entities/order.dart';
import 'package:order_tracker/features/orders/domain/entities/order_id.dart';
import 'package:order_tracker/features/orders/domain/entities/order_status.dart';
import 'package:order_tracker/features/orders/domain/entities/orders_filter.dart';
import 'package:order_tracker/features/orders/domain/usecases/list_orders.dart';
import 'package:order_tracker/features/orders/domain/usecases/search_order.dart';

import '../../../fixtures/order_fixtures.dart';
import '../../../helpers/mocks.dart';

void main() {
  late MockOrderRepository repository;

  setUpAll(() => registerFallbackValue(const OrdersFilter()));

  setUp(() => repository = MockOrderRepository());

  group('OrderId', () {
    test('should normalize and validate identifiers', () {
      expect(OrderId.normalize('  ord-mx-000147 '), approvedOrderId);
      expect(OrderId.validate('   '), OrderIdError.empty);
      expect(OrderId.validate('x' * (OrderId.maxLength + 1)), OrderIdError.tooLong);
      expect(OrderId.validate('x' * OrderId.maxLength), isNull);
    });
  });

  group('SearchOrder', () {
    test('should query the repository with the normalized id', () async {
      when(() => repository.findById(any())).thenAnswer((_) async => Ok(approvedOrder()));
      final result = await SearchOrder(repository)(' ord-mx-000147 ');
      expect(result, Ok<Order>(approvedOrder()));
      verify(() => repository.findById(approvedOrderId)).called(1);
    });

    test('should reject invalid ids without calling the repository', () async {
      final result = await SearchOrder(repository)('  ');
      expect(result, const Err<Order>(ValidationFailure()));
      verifyNever(() => repository.findById(any()));
    });
  });

  group('ListOrders', () {
    test('should request pages with the standard page size', () async {
      const filter = OrdersFilter(market: Market.pe);
      final page = orderPage(items: [summary('A')]);
      when(
        () => repository.list(
          filter: any(named: 'filter'),
          page: any(named: 'page'),
          size: any(named: 'size'),
        ),
      ).thenAnswer((_) async => Ok(page));
      expect(await ListOrders(repository)(filter: filter, page: 2), Ok(page));
      verify(
        () => repository.list(filter: filter, page: 2, size: OrdersFilter.pageSize),
      ).called(1);
    });
  });

  group('entities', () {
    test('should update filters immutably', () {
      const filter = OrdersFilter(status: OrderStatus.approved, market: Market.mx);
      expect(filter.withStatus(null), const OrdersFilter(market: Market.mx));
      expect(filter.withMarket(Market.co).market, Market.co);
      expect(filter.withMarket(Market.co).status, OrderStatus.approved);
    });

    test('should expose derived order properties', () {
      expect(rejectedOrder().isRejected, isTrue);
      expect(approvedOrder().isRejected, isFalse);
      expect(failedOrder().isTechnicalFailure, isTrue);
      expect(detailedLine.displayName, 'Bebida 600 ml');
      expect(bareLine.displayName, 'PRD-008');
    });

    test('should know whether more pages exist', () {
      expect(orderPage(items: const [], totalPages: 2).hasMore, isTrue);
      expect(orderPage(items: const [], page: 1, totalPages: 2).hasMore, isFalse);
      expect(orderPage(items: const [], totalPages: 0).hasMore, isFalse);
    });
  });
}
