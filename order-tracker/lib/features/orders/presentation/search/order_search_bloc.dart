import 'package:bloc_concurrency/bloc_concurrency.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:order_tracker/core/error/app_failure.dart';
import 'package:order_tracker/core/result/result.dart';
import 'package:order_tracker/features/orders/domain/entities/order.dart';
import 'package:order_tracker/features/orders/domain/entities/order_id.dart';
import 'package:order_tracker/features/orders/domain/usecases/search_order.dart';
import 'package:order_tracker/features/orders/presentation/search/order_search_event.dart';
import 'package:order_tracker/features/orders/presentation/search/order_search_state.dart';

class OrderSearchBloc extends Bloc<OrderSearchEvent, OrderSearchState> {
  OrderSearchBloc(this._searchOrder) : super(const OrderSearchIdle()) {
    on<OrderSearchEvent>(_onEvent, transformer: restartable());
  }

  final SearchOrder _searchOrder;

  Future<void> _onEvent(OrderSearchEvent event, Emitter<OrderSearchState> emit) async {
    switch (event) {
      case OrderSearchSubmitted(:final orderId):
        await _search(OrderId.normalize(orderId), emit);
      case OrderSearchRetried():
        final orderId = state.orderId;
        if (orderId != null) {
          await _search(orderId, emit);
        }
      case OrderSearchCleared():
        emit(const OrderSearchIdle());
    }
  }

  Future<void> _search(String orderId, Emitter<OrderSearchState> emit) async {
    emit(OrderSearchLoading(orderId));
    final result = await _searchOrder(orderId);
    emit(_stateFor(orderId, result));
  }

  OrderSearchState _stateFor(String orderId, Result<Order> result) {
    return switch (result) {
      Ok<Order>(:final value) => OrderSearchSuccess(value),
      Err<Order>(failure: NotFoundFailure()) => OrderSearchNotFound(orderId),
      Err<Order>(:final failure) => OrderSearchFailure(orderId, failure),
    };
  }
}
