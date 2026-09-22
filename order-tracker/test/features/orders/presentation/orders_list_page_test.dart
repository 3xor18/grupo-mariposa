import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:order_tracker/core/error/app_failure.dart';
import 'package:order_tracker/core/l10n/app_strings.dart';
import 'package:order_tracker/core/result/result.dart';
import 'package:order_tracker/core/widgets/failure_view.dart';
import 'package:order_tracker/features/orders/domain/entities/market.dart';
import 'package:order_tracker/features/orders/domain/entities/order_status.dart';
import 'package:order_tracker/features/orders/domain/entities/order_summary.dart';
import 'package:order_tracker/features/orders/domain/entities/orders_filter.dart';
import 'package:order_tracker/features/orders/presentation/orders_keys.dart';
import 'package:order_tracker/features/orders/presentation/pages/order_detail_page.dart';
import 'package:order_tracker/features/orders/presentation/pages/orders_list_page.dart';

import '../../../fixtures/order_fixtures.dart';
import '../../../helpers/mocks.dart';
import '../../../helpers/pump_app.dart';

void main() {
  late MockOrderRepository repository;
  final firstPage = orderPage(items: [summary(approvedOrderId), summary('ORD-2')], totalPages: 2);
  final secondPage = orderPage(
    items: [summary('ORD-3', status: OrderStatus.rejected)],
    page: 1,
    totalPages: 2,
  );

  setUpAll(() async {
    registerFallbackValue(const OrdersFilter());
    await initializeTestLocale();
  });

  setUp(() => repository = MockOrderRepository());

  void answerList(Future<Result<OrderPage>> Function(int page) respond) {
    when(
      () => repository.list(
        filter: any(named: 'filter'),
        page: any(named: 'page'),
        size: any(named: 'size'),
      ),
    ).thenAnswer((invocation) => respond(invocation.namedArguments[#page] as int));
  }

  void answerPages() {
    answerList((page) async => Ok(page == 0 ? firstPage : secondPage));
  }

  Future<void> pumpPage(WidgetTester tester, {Size size = phoneSize}) async {
    await tester.pumpOrdersApp(const OrdersListPage(), repository: repository, size: size);
  }

  testWidgets('should show loading and then the first page', (tester) async {
    final pending = Completer<Result<OrderPage>>();
    answerList((_) => pending.future);
    await pumpPage(tester);
    await tester.pump();
    expect(find.byKey(OrdersKeys.ordersLoading), findsOneWidget);
    pending.complete(Ok(firstPage));
    await tester.pumpAndSettle();
    expect(find.byKey(OrdersKeys.summaryTile(approvedOrderId)), findsOneWidget);
    expect(find.byKey(OrdersKeys.loadMoreButton), findsOneWidget);
    expect(find.text(AppStrings.recentOrders), findsOneWidget);
  });

  testWidgets('should show the empty state when no order matches', (tester) async {
    answerList((_) async => Ok(orderPage(items: const [], totalPages: 0)));
    await pumpPage(tester);
    await tester.pumpAndSettle();
    expect(find.byKey(OrdersKeys.ordersEmpty), findsOneWidget);
    expect(find.text(AppStrings.emptyListMessage), findsOneWidget);
  });

  testWidgets('should show an error and retry the first page', (tester) async {
    answerList((_) async => const Err(NetworkFailure()));
    await pumpPage(tester);
    await tester.pumpAndSettle();
    expect(find.byKey(OrdersKeys.ordersFailure), findsOneWidget);
    answerPages();
    await tester.tap(find.byKey(FailureView.retryButtonKey));
    await tester.pumpAndSettle();
    expect(find.byKey(OrdersKeys.summaryTile('ORD-2')), findsOneWidget);
  });

  testWidgets('should filter by status and market with chips', (tester) async {
    answerPages();
    await pumpPage(tester);
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(OrdersKeys.statusFilter(OrderStatus.rejected)));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(OrdersKeys.marketFilter(Market.co)));
    await tester.pumpAndSettle();
    verify(
      () => repository.list(
        filter: const OrdersFilter(status: OrderStatus.rejected, market: Market.co),
        page: 0,
        size: OrdersFilter.pageSize,
      ),
    ).called(1);
    final chip = tester.widget<FilterChip>(
      find.byKey(OrdersKeys.statusFilter(OrderStatus.rejected)),
    );
    expect(chip.selected, isTrue);
  });

  testWidgets('should load more orders on demand', (tester) async {
    answerPages();
    await pumpPage(tester);
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(OrdersKeys.loadMoreButton));
    await tester.pumpAndSettle();
    expect(find.byKey(OrdersKeys.summaryTile('ORD-3')), findsOneWidget);
    expect(find.byKey(OrdersKeys.loadMoreButton), findsNothing);
  });

  testWidgets('should show progress while the next page loads', (tester) async {
    final pending = Completer<Result<OrderPage>>();
    answerList((page) => page == 0 ? Future.value(Ok(firstPage)) : pending.future);
    await pumpPage(tester);
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(OrdersKeys.loadMoreButton));
    await tester.pump();
    expect(find.byKey(OrdersKeys.loadMoreProgress), findsOneWidget);
    pending.complete(Ok(secondPage));
    await tester.pumpAndSettle();
    expect(find.byKey(OrdersKeys.loadMoreProgress), findsNothing);
  });

  testWidgets('should show next page errors inline with retry', (tester) async {
    answerList(
      (page) async => page == 0 ? Ok(firstPage) : const Err(ServerFailure(statusCode: 502)),
    );
    await pumpPage(tester);
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(OrdersKeys.loadMoreButton));
    await tester.pumpAndSettle();
    expect(find.textContaining(AppStrings.nextPageFailed), findsOneWidget);
    answerPages();
    await tester.tap(find.byKey(OrdersKeys.nextPageRetryButton));
    await tester.pumpAndSettle();
    expect(find.byKey(OrdersKeys.summaryTile('ORD-3')), findsOneWidget);
  });

  testWidgets('should refresh with pull to refresh', (tester) async {
    answerPages();
    await pumpPage(tester);
    await tester.pumpAndSettle();
    answerList((_) async => Ok(orderPage(items: [summary('ORD-NEW')])));
    await tester.fling(find.byKey(OrdersKeys.ordersList), const Offset(0, 400), 1000);
    await tester.pumpAndSettle();
    expect(find.byKey(OrdersKeys.summaryTile('ORD-NEW')), findsOneWidget);
  });

  testWidgets('should keep the list and notify when a refresh fails', (tester) async {
    answerPages();
    await pumpPage(tester);
    await tester.pumpAndSettle();
    answerList((_) async => const Err(NetworkFailure()));
    await tester.fling(find.byKey(OrdersKeys.ordersList), const Offset(0, 400), 1000);
    await tester.pumpAndSettle();
    expect(find.textContaining(AppStrings.refreshFailed), findsOneWidget);
    expect(find.byKey(OrdersKeys.summaryTile(approvedOrderId)), findsOneWidget);
  });

  testWidgets('should open the order detail page on phones', (tester) async {
    answerPages();
    when(() => repository.findById(any())).thenAnswer((_) async => Ok(approvedOrder()));
    await pumpPage(tester);
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(OrdersKeys.summaryTile(approvedOrderId)));
    await tester.pumpAndSettle();
    expect(find.byType(OrderDetailPage), findsOneWidget);
    expect(find.byKey(OrdersKeys.detailView), findsOneWidget);
    expect(find.byKey(OrdersKeys.detailPane), findsNothing);
  });

  testWidgets('should show list and detail side by side on wide screens', (tester) async {
    answerPages();
    when(() => repository.findById(any())).thenAnswer((_) async => Ok(approvedOrder()));
    await pumpPage(tester, size: desktopSize);
    await tester.pumpAndSettle();
    expect(find.byKey(OrdersKeys.detailPlaceholder), findsOneWidget);
    await tester.tap(find.byKey(OrdersKeys.summaryTile(approvedOrderId)));
    await tester.pumpAndSettle();
    expect(find.byType(OrderDetailPage), findsNothing);
    expect(find.byKey(OrdersKeys.detailView), findsOneWidget);
    final tile = tester.widget<ListTile>(find.byKey(OrdersKeys.summaryTile(approvedOrderId)));
    expect(tile.selected, isTrue);
  });
}
