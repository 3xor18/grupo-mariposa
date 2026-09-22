import 'package:equatable/equatable.dart';
import 'package:order_tracker/core/error/app_failure.dart';
import 'package:order_tracker/features/orders/domain/entities/order_page.dart';
import 'package:order_tracker/features/orders/domain/entities/order_summary.dart';
import 'package:order_tracker/features/orders/domain/entities/orders_filter.dart';

enum OrdersListStatus { initial, loading, success, failure }

final class OrdersListState extends Equatable {
  const OrdersListState({
    this.filter = const OrdersFilter(),
    this.status = OrdersListStatus.initial,
    this.items = const [],
    this.page = OrdersFilter.firstPage,
    this.hasMore = false,
    this.generation = 0,
    this.isRefreshing = false,
    this.isLoadingMore = false,
    this.failure,
    this.refreshFailure,
    this.nextPageFailure,
  });

  final OrdersFilter filter;
  final OrdersListStatus status;
  final List<OrderSummary> items;
  final int page;
  final bool hasMore;
  final int generation;
  final bool isRefreshing;
  final bool isLoadingMore;
  final AppFailure? failure;
  final AppFailure? refreshFailure;
  final AppFailure? nextPageFailure;

  bool get isEmpty => status == OrdersListStatus.success && items.isEmpty;

  bool get canLoadMore => status == OrdersListStatus.success && hasMore && !isLoadingMore;

  OrdersListState copyWith({
    OrdersListStatus? status,
    List<OrderSummary>? items,
    int? page,
    bool? hasMore,
    bool? isRefreshing,
    bool? isLoadingMore,
    AppFailure? failure,
    AppFailure? refreshFailure,
    AppFailure? nextPageFailure,
  }) {
    return OrdersListState(
      filter: filter,
      status: status ?? this.status,
      items: items ?? this.items,
      page: page ?? this.page,
      hasMore: hasMore ?? this.hasMore,
      generation: generation,
      isRefreshing: isRefreshing ?? this.isRefreshing,
      isLoadingMore: isLoadingMore ?? this.isLoadingMore,
      failure: failure,
      refreshFailure: refreshFailure,
      nextPageFailure: nextPageFailure,
    );
  }

  OrdersListState withPage(OrderPage result, {required bool append}) {
    return copyWith(
      status: OrdersListStatus.success,
      items: append ? [...items, ...result.items] : result.items,
      page: result.page,
      hasMore: result.hasMore,
      isRefreshing: false,
      isLoadingMore: false,
    );
  }

  @override
  List<Object?> get props => [
    filter,
    status,
    items,
    page,
    hasMore,
    generation,
    isRefreshing,
    isLoadingMore,
    failure,
    refreshFailure,
    nextPageFailure,
  ];
}
