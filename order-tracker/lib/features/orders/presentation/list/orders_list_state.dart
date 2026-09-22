import 'package:equatable/equatable.dart';
import 'package:order_tracker/core/error/app_failure.dart';
import 'package:order_tracker/features/orders/domain/entities/order_page.dart';
import 'package:order_tracker/features/orders/domain/entities/order_summary.dart';
import 'package:order_tracker/features/orders/domain/entities/orders_filter.dart';

enum OrdersListStatus { initial, loading, success, failure }

typedef FailureUpdate = AppFailure? Function();

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

  bool get canLoadMore {
    return status == OrdersListStatus.success && hasMore && !isLoadingMore && !isRefreshing;
  }

  OrdersListState copyWith({
    OrdersListStatus? status,
    List<OrderSummary>? items,
    int? page,
    bool? hasMore,
    int? generation,
    bool? isRefreshing,
    bool? isLoadingMore,
    FailureUpdate? failure,
    FailureUpdate? refreshFailure,
    FailureUpdate? nextPageFailure,
  }) {
    return OrdersListState(
      filter: filter,
      status: status ?? this.status,
      items: items ?? this.items,
      page: page ?? this.page,
      hasMore: hasMore ?? this.hasMore,
      generation: generation ?? this.generation,
      isRefreshing: isRefreshing ?? this.isRefreshing,
      isLoadingMore: isLoadingMore ?? this.isLoadingMore,
      failure: failure == null ? this.failure : failure(),
      refreshFailure: refreshFailure == null ? this.refreshFailure : refreshFailure(),
      nextPageFailure: nextPageFailure == null ? this.nextPageFailure : nextPageFailure(),
    );
  }

  OrdersListState withFirstPage(OrderPage result) {
    return copyWith(
      status: OrdersListStatus.success,
      items: _unique(result.items),
      page: result.page,
      hasMore: result.hasMore,
      generation: generation + 1,
      isRefreshing: false,
      isLoadingMore: false,
      failure: clearFailure,
      refreshFailure: clearFailure,
      nextPageFailure: clearFailure,
    );
  }

  OrdersListState withNextPage(OrderPage result) {
    return copyWith(
      items: _unique([...items, ...result.items]),
      page: result.page,
      hasMore: result.hasMore,
      isLoadingMore: false,
      nextPageFailure: clearFailure,
    );
  }

  static AppFailure? clearFailure() => null;

  static List<OrderSummary> _unique(List<OrderSummary> summaries) {
    final seen = <String>{};
    return [
      for (final summary in summaries)
        if (seen.add(summary.orderId)) summary,
    ];
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
