import 'dart:async';

import 'package:bloc_test/bloc_test.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:order_tracker/core/error/app_failure.dart';
import 'package:order_tracker/core/result/result.dart';
import 'package:order_tracker/features/orders/domain/entities/order.dart';
import 'package:order_tracker/features/orders/domain/usecases/search_order.dart';
import 'package:order_tracker/features/orders/presentation/search/order_search_bloc.dart';
import 'package:order_tracker/features/orders/presentation/search/order_search_event.dart';
import 'package:order_tracker/features/orders/presentation/search/order_search_state.dart';

import '../../../fixtures/order_fixtures.dart';
import '../../../helpers/mocks.dart';

void main() {
  late MockOrderRepository repository;

  setUp(() => repository = MockOrderRepository());

  OrderSearchBloc buildBloc() => OrderSearchBloc(SearchOrder(repository));

  void answer(String orderId, Result<Order> result) {
    when(() => repository.findById(orderId)).thenAnswer((_) async => result);
  }

  test('should start idle', () {
    expect(buildBloc().state, const OrderSearchIdle());
    expect(const OrderSearchIdle().orderId, isNull);
  });

  blocTest<OrderSearchBloc, OrderSearchState>(
    'should emit loading then success when the order exists',
    setUp: () => answer(approvedOrderId, Ok(approvedOrder())),
    build: buildBloc,
    act: (bloc) => bloc.add(const OrderSearchSubmitted(' ord-mx-000147 ')),
    expect: () => [
      const OrderSearchLoading(approvedOrderId),
      OrderSearchSuccess(approvedOrder()),
    ],
  );

  blocTest<OrderSearchBloc, OrderSearchState>(
    'should emit not found when the api answers ORDER_NOT_FOUND',
    setUp: () => answer('ORD-404', const Err(NotFoundFailure(traceId: 't'))),
    build: buildBloc,
    act: (bloc) => bloc.add(const OrderSearchSubmitted('ORD-404')),
    expect: () => const [OrderSearchLoading('ORD-404'), OrderSearchNotFound('ORD-404')],
  );

  blocTest<OrderSearchBloc, OrderSearchState>(
    'should emit failure for transport errors',
    setUp: () => answer('ORD-1', const Err(NetworkFailure())),
    build: buildBloc,
    act: (bloc) => bloc.add(const OrderSearchSubmitted('ORD-1')),
    expect: () => const [
      OrderSearchLoading('ORD-1'),
      OrderSearchFailure('ORD-1', NetworkFailure()),
    ],
  );

  blocTest<OrderSearchBloc, OrderSearchState>(
    'should retry the last failed search',
    setUp: () => answer(approvedOrderId, Ok(approvedOrder())),
    build: buildBloc,
    seed: () => const OrderSearchFailure(approvedOrderId, ServerFailure(statusCode: 503)),
    act: (bloc) => bloc.add(const OrderSearchRetried()),
    expect: () => [
      const OrderSearchLoading(approvedOrderId),
      OrderSearchSuccess(approvedOrder()),
    ],
  );

  blocTest<OrderSearchBloc, OrderSearchState>(
    'should ignore retries when nothing was searched',
    build: buildBloc,
    act: (bloc) => bloc.add(const OrderSearchRetried()),
    expect: () => const <OrderSearchState>[],
  );

  blocTest<OrderSearchBloc, OrderSearchState>(
    'should clear back to idle',
    build: buildBloc,
    seed: () => const OrderSearchNotFound('ORD-404'),
    act: (bloc) => bloc.add(const OrderSearchCleared()),
    expect: () => const [OrderSearchIdle()],
  );

  group('late responses', () {
    late Completer<Result<Order>> slowResponse;

    setUp(() {
      slowResponse = Completer<Result<Order>>();
      when(() => repository.findById('ORD-SLOW')).thenAnswer((_) => slowResponse.future);
      answer(approvedOrderId, Ok(approvedOrder()));
    });

    blocTest<OrderSearchBloc, OrderSearchState>(
      'should render only the newest search when an older response arrives later',
      build: buildBloc,
      act: (bloc) async {
        bloc.add(const OrderSearchSubmitted('ORD-SLOW'));
        await pumpEventQueue();
        bloc.add(const OrderSearchSubmitted(approvedOrderId));
        await pumpEventQueue();
        slowResponse.complete(Ok(rejectedOrder()));
        await pumpEventQueue();
      },
      expect: () => [
        const OrderSearchLoading('ORD-SLOW'),
        const OrderSearchLoading(approvedOrderId),
        OrderSearchSuccess(approvedOrder()),
      ],
      verify: (bloc) => expect(bloc.state, OrderSearchSuccess(approvedOrder())),
    );

    blocTest<OrderSearchBloc, OrderSearchState>(
      'should discard a pending response when the search is cleared',
      build: buildBloc,
      act: (bloc) async {
        bloc.add(const OrderSearchSubmitted('ORD-SLOW'));
        await pumpEventQueue();
        bloc.add(const OrderSearchCleared());
        await pumpEventQueue();
        slowResponse.complete(const Err(NetworkFailure()));
        await pumpEventQueue();
      },
      expect: () => const [OrderSearchLoading('ORD-SLOW'), OrderSearchIdle()],
    );
  });
}
