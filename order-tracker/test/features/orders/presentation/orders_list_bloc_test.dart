import 'dart:async';

import 'package:bloc_test/bloc_test.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:order_tracker/core/error/app_failure.dart';
import 'package:order_tracker/core/result/result.dart';
import 'package:order_tracker/features/orders/domain/entities/market.dart';
import 'package:order_tracker/features/orders/domain/entities/order_page.dart';
import 'package:order_tracker/features/orders/domain/entities/order_status.dart';
import 'package:order_tracker/features/orders/domain/entities/order_summary.dart';
import 'package:order_tracker/features/orders/domain/entities/orders_filter.dart';
import 'package:order_tracker/features/orders/domain/usecases/list_orders.dart';
import 'package:order_tracker/features/orders/presentation/list/orders_list_bloc.dart';
import 'package:order_tracker/features/orders/presentation/list/orders_list_event.dart';
import 'package:order_tracker/features/orders/presentation/list/orders_list_state.dart';

import '../../../fixtures/order_fixtures.dart';
import '../../../helpers/mocks.dart';

void main() {
  late MockOrderRepository repository;
  final firstPage = orderPage(items: [summary('A'), summary('B')], totalPages: 2);
  final secondPage = orderPage(items: [summary('C')], page: 1, totalPages: 2);
  const loaded = OrdersListStatus.success;

  setUpAll(() => registerFallbackValue(const OrdersFilter()));

  setUp(() => repository = MockOrderRepository());

  OrdersListBloc buildBloc() => OrdersListBloc(ListOrders(repository));

  void answer(Result<OrderPage> Function(OrdersFilter filter, int page) respond) {
    when(
      () => repository.list(
        filter: any(named: 'filter'),
        page: any(named: 'page'),
        size: any(named: 'size'),
      ),
    ).thenAnswer((invocation) async {
      final filter = invocation.namedArguments[#filter] as OrdersFilter;
      final page = invocation.namedArguments[#page] as int;
      return respond(filter, page);
    });
  }

  OrdersListState loadedState({
    List<OrderSummary>? items,
    int page = 0,
    bool hasMore = true,
    int generation = 1,
    OrdersFilter filter = const OrdersFilter(),
  }) {
    return OrdersListState(
      filter: filter,
      status: loaded,
      items: items ?? firstPage.items,
      page: page,
      hasMore: hasMore,
      generation: generation,
    );
  }

  test('should expose derived flags', () {
    expect(const OrdersListState().isEmpty, isFalse);
    expect(const OrdersListState(status: loaded).isEmpty, isTrue);
    expect(loadedState().canLoadMore, isTrue);
    expect(loadedState(hasMore: false).canLoadMore, isFalse);
  });

  blocTest<OrdersListBloc, OrdersListState>(
    'should load the first page',
    setUp: () => answer((_, _) => Ok(firstPage)),
    build: buildBloc,
    act: (bloc) => bloc.add(const OrdersListRequested()),
    expect: () => [
      const OrdersListState(status: OrdersListStatus.loading, generation: 1),
      loadedState(),
    ],
  );

  blocTest<OrdersListBloc, OrdersListState>(
    'should emit a blocking failure when the first page fails',
    setUp: () => answer((_, _) => const Err(NetworkFailure())),
    build: buildBloc,
    act: (bloc) => bloc.add(const OrdersListRequested()),
    expect: () => const [
      OrdersListState(status: OrdersListStatus.loading, generation: 1),
      OrdersListState(
        status: OrdersListStatus.failure,
        generation: 1,
        failure: NetworkFailure(),
      ),
    ],
  );

  blocTest<OrdersListBloc, OrdersListState>(
    'should toggle status and market filters and reload from the first page',
    setUp: () => answer((_, _) => Ok(firstPage)),
    build: buildBloc,
    seed: loadedState,
    act: (bloc) => bloc
      ..add(const OrdersListStatusToggled(OrderStatus.rejected))
      ..add(const OrdersListMarketToggled(Market.co))
      ..add(const OrdersListStatusToggled(OrderStatus.rejected)),
    wait: const Duration(milliseconds: 10),
    verify: (bloc) {
      expect(bloc.state.filter, const OrdersFilter(market: Market.co));
      expect(bloc.state.status, loaded);
    },
  );

  blocTest<OrdersListBloc, OrdersListState>(
    'should untoggle the selected market',
    setUp: () => answer((_, _) => Ok(firstPage)),
    build: buildBloc,
    seed: () => loadedState(filter: const OrdersFilter(market: Market.mx)),
    act: (bloc) => bloc.add(const OrdersListMarketToggled(Market.mx)),
    verify: (bloc) => expect(bloc.state.filter, const OrdersFilter()),
  );

  blocTest<OrdersListBloc, OrdersListState>(
    'should append the next page',
    setUp: () => answer((_, page) => Ok(page == 0 ? firstPage : secondPage)),
    build: buildBloc,
    seed: loadedState,
    act: (bloc) => bloc.add(const OrdersListNextPageRequested()),
    expect: () => [
      loadedState().copyWith(isLoadingMore: true),
      loadedState(items: [...firstPage.items, ...secondPage.items], page: 1, hasMore: false),
    ],
  );

  blocTest<OrdersListBloc, OrdersListState>(
    'should ignore next page requests when there are no more pages',
    build: buildBloc,
    seed: () => loadedState(hasMore: false),
    act: (bloc) => bloc.add(const OrdersListNextPageRequested()),
    expect: () => const <OrdersListState>[],
  );

  blocTest<OrdersListBloc, OrdersListState>(
    'should keep loaded items and report next page failures inline',
    setUp: () => answer((_, _) => const Err(ServerFailure(statusCode: 503))),
    build: buildBloc,
    seed: loadedState,
    act: (bloc) => bloc.add(const OrdersListNextPageRequested()),
    expect: () => [
      loadedState().copyWith(isLoadingMore: true),
      loadedState().copyWith(nextPageFailure: const ServerFailure(statusCode: 503)),
    ],
  );

  blocTest<OrdersListBloc, OrdersListState>(
    'should refresh keeping items visible',
    setUp: () => answer((_, _) => Ok(orderPage(items: [summary('Z')]))),
    build: buildBloc,
    seed: loadedState,
    act: (bloc) => bloc.add(const OrdersListRefreshed()),
    expect: () => [
      OrdersListState(
        status: loaded,
        items: firstPage.items,
        hasMore: true,
        generation: 2,
        isRefreshing: true,
      ),
      loadedState(items: [summary('Z')], hasMore: false, generation: 2),
    ],
  );

  blocTest<OrdersListBloc, OrdersListState>(
    'should keep items and report refresh failures without blocking',
    setUp: () => answer((_, _) => const Err(NetworkFailure())),
    build: buildBloc,
    seed: loadedState,
    act: (bloc) => bloc.add(const OrdersListRefreshed()),
    skip: 1,
    expect: () => [
      loadedState(generation: 2).copyWith(refreshFailure: const NetworkFailure()),
    ],
  );

  blocTest<OrdersListBloc, OrdersListState>(
    'should perform a full load when refreshing an empty list',
    setUp: () => answer((_, _) => Ok(firstPage)),
    build: buildBloc,
    seed: () => const OrdersListState(status: OrdersListStatus.failure, failure: NetworkFailure()),
    act: (bloc) => bloc.add(const OrdersListRefreshed()),
    expect: () => [
      const OrdersListState(status: OrdersListStatus.loading, generation: 1),
      loadedState(),
    ],
  );

  group('late responses', () {
    late Completer<Result<OrderPage>> slow;

    setUp(() => slow = Completer<Result<OrderPage>>());

    blocTest<OrdersListBloc, OrdersListState>(
      'should discard a slow first page after the filter changes',
      setUp: () => answer((filter, _) {
        return filter.status == null
            ? const Ok(OrderPage(items: [], page: 0, size: 20, totalElements: 0, totalPages: 0))
            : Ok(firstPage);
      }),
      build: buildBloc,
      act: (bloc) async {
        when(
          () => repository.list(filter: const OrdersFilter(), page: 0, size: 20),
        ).thenAnswer((_) => slow.future);
        bloc.add(const OrdersListRequested());
        await pumpEventQueue();
        bloc.add(const OrdersListStatusToggled(OrderStatus.approved));
        await pumpEventQueue();
        slow.complete(Ok(orderPage(items: [summary('STALE')])));
        await pumpEventQueue();
      },
      verify: (bloc) {
        expect(bloc.state.items, firstPage.items);
        expect(bloc.state.filter.status, OrderStatus.approved);
      },
    );

    blocTest<OrdersListBloc, OrdersListState>(
      'should discard a slow next page after the list is reloaded',
      setUp: () => answer((_, page) => Ok(firstPage)),
      build: buildBloc,
      seed: loadedState,
      act: (bloc) async {
        when(
          () => repository.list(filter: const OrdersFilter(), page: 1, size: 20),
        ).thenAnswer((_) => slow.future);
        bloc.add(const OrdersListNextPageRequested());
        await pumpEventQueue();
        bloc.add(const OrdersListRefreshed());
        await pumpEventQueue();
        slow.complete(Ok(secondPage));
        await pumpEventQueue();
      },
      verify: (bloc) {
        expect(bloc.state.items, firstPage.items);
        expect(bloc.state.isLoadingMore, isFalse);
        expect(bloc.state.generation, 2);
      },
    );
  });
}
