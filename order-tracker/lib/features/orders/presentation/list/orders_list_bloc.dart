import 'package:bloc_concurrency/bloc_concurrency.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:order_tracker/core/result/result.dart';
import 'package:order_tracker/features/orders/domain/entities/order_summary.dart';
import 'package:order_tracker/features/orders/domain/entities/orders_filter.dart';
import 'package:order_tracker/features/orders/domain/usecases/list_orders.dart';
import 'package:order_tracker/features/orders/presentation/list/orders_list_event.dart';
import 'package:order_tracker/features/orders/presentation/list/orders_list_state.dart';

class OrdersListBloc extends Bloc<OrdersListEvent, OrdersListState> {
  OrdersListBloc(this._listOrders) : super(const OrdersListState()) {
    on<OrdersListQueryEvent>(_onQuery, transformer: restartable());
    on<OrdersListNextPageRequested>(_onNextPage, transformer: droppable());
  }

  final ListOrders _listOrders;

  Future<void> _onQuery(OrdersListQueryEvent event, Emitter<OrdersListState> emit) async {
    final filter = _filterFor(event);
    final keepsItems = event is OrdersListRefreshed && state.items.isNotEmpty;
    emit(_startingState(filter, keepsItems: keepsItems));
    final result = await _listOrders(filter: filter, page: OrdersFilter.firstPage);
    emit(switch (result) {
      Ok<OrderPage>(:final value) => state.withPage(value, append: false),
      Err<OrderPage>(:final failure) when keepsItems => state.copyWith(
        isRefreshing: false,
        refreshFailure: failure,
      ),
      Err<OrderPage>(:final failure) => state.copyWith(
        status: OrdersListStatus.failure,
        failure: failure,
      ),
    });
  }

  Future<void> _onNextPage(
    OrdersListNextPageRequested event,
    Emitter<OrdersListState> emit,
  ) async {
    if (!state.canLoadMore) {
      return;
    }
    final requested = state;
    emit(requested.copyWith(isLoadingMore: true));
    final result = await _listOrders(filter: requested.filter, page: requested.page + 1);
    if (state.generation != requested.generation) {
      return;
    }
    emit(switch (result) {
      Ok<OrderPage>(:final value) => state.withPage(value, append: true),
      Err<OrderPage>(:final failure) => state.copyWith(
        isLoadingMore: false,
        nextPageFailure: failure,
      ),
    });
  }

  OrdersFilter _filterFor(OrdersListQueryEvent event) {
    final filter = state.filter;
    return switch (event) {
      OrdersListStatusToggled(:final status) => filter.withStatus(
        filter.status == status ? null : status,
      ),
      OrdersListMarketToggled(:final market) => filter.withMarket(
        filter.market == market ? null : market,
      ),
      OrdersListRequested() || OrdersListRefreshed() => filter,
    };
  }

  OrdersListState _startingState(OrdersFilter filter, {required bool keepsItems}) {
    final generation = state.generation + 1;
    if (keepsItems) {
      return OrdersListState(
        filter: filter,
        status: state.status,
        items: state.items,
        page: state.page,
        hasMore: state.hasMore,
        generation: generation,
        isRefreshing: true,
      );
    }
    return OrdersListState(
      filter: filter,
      status: OrdersListStatus.loading,
      generation: generation,
    );
  }
}
