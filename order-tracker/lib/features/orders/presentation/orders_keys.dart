import 'package:flutter/widgets.dart';
import 'package:order_tracker/features/orders/domain/entities/market.dart';
import 'package:order_tracker/features/orders/domain/entities/order_status.dart';

abstract final class OrdersKeys {
  static const searchField = Key('orderSearchField');
  static const searchButton = Key('orderSearchButton');
  static const clearButton = Key('orderSearchClearButton');
  static const idleView = Key('orderSearchIdle');
  static const loadingView = Key('orderSearchLoading');
  static const notFoundView = Key('orderSearchNotFound');
  static const failureView = Key('orderSearchFailure');
  static const detailView = Key('orderDetail');
  static const statusChip = Key('orderStatusChip');
  static const grandTotal = Key('orderGrandTotal');
  static const linesCard = Key('orderLinesCard');
  static const totalsCard = Key('orderTotalsCard');
  static const rejectionCard = Key('orderRejectionCard');
  static const failureCard = Key('orderFailureCard');
  static const ordersList = Key('ordersList');
  static const ordersLoading = Key('ordersLoading');
  static const ordersEmpty = Key('ordersEmpty');
  static const ordersFailure = Key('ordersFailure');
  static const loadMoreButton = Key('ordersLoadMoreButton');
  static const loadMoreProgress = Key('ordersLoadMoreProgress');
  static const nextPageRetryButton = Key('ordersNextPageRetryButton');
  static const detailPane = Key('ordersDetailPane');
  static const detailPlaceholder = Key('ordersDetailPlaceholder');

  static Key statusFilter(OrderStatus status) => Key('statusFilter-${status.name}');

  static Key marketFilter(Market market) => Key('marketFilter-${market.name}');

  static Key summaryTile(String orderId) => Key('orderTile-$orderId');
}
