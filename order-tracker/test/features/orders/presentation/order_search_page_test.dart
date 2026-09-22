import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:order_tracker/core/error/app_failure.dart';
import 'package:order_tracker/core/l10n/app_strings.dart';
import 'package:order_tracker/core/money/unit_price.dart';
import 'package:order_tracker/core/result/result.dart';
import 'package:order_tracker/core/widgets/failure_view.dart';
import 'package:order_tracker/features/orders/domain/entities/order.dart';
import 'package:order_tracker/features/orders/domain/entities/order_id.dart';
import 'package:order_tracker/features/orders/domain/entities/order_line.dart';
import 'package:order_tracker/features/orders/presentation/orders_keys.dart';
import 'package:order_tracker/features/orders/presentation/pages/order_search_page.dart';

import '../../../fixtures/order_fixtures.dart';
import '../../../helpers/mocks.dart';
import '../../../helpers/pump_app.dart';

void main() {
  late MockOrderRepository repository;

  setUpAll(initializeTestLocale);

  setUp(() => repository = MockOrderRepository());

  void answer(Result<Order> result) {
    when(() => repository.findById(any())).thenAnswer((_) async => result);
  }

  Future<void> search(WidgetTester tester, String orderId) async {
    await tester.enterText(find.byKey(OrdersKeys.searchField), orderId);
    await tester.tap(find.byKey(OrdersKeys.searchButton));
    await tester.pump();
  }

  Future<void> pumpPage(WidgetTester tester, {Size size = phoneSize}) {
    return tester.pumpOrdersApp(const OrderSearchPage(), repository: repository, size: size);
  }

  testWidgets('should show the idle state before any search', (tester) async {
    await pumpPage(tester);
    expect(find.byKey(OrdersKeys.idleView), findsOneWidget);
    expect(find.text(AppStrings.searchIdleTitle), findsOneWidget);
    expect(find.bySemanticsLabel(AppStrings.orderIdLabel), findsOneWidget);
  });

  testWidgets('should show loading while the order is requested', (tester) async {
    final pending = Completer<Result<Order>>();
    when(() => repository.findById(any())).thenAnswer((_) => pending.future);
    await pumpPage(tester);
    await search(tester, approvedOrderId);
    expect(find.byKey(OrdersKeys.loadingView), findsOneWidget);
    expect(find.bySemanticsLabel(AppStrings.loadingOrder), findsOneWidget);
    pending.complete(Ok(approvedOrder()));
    await tester.pumpAndSettle();
    expect(find.byKey(OrdersKeys.detailView), findsOneWidget);
  });

  testWidgets('should render status, totals and lines of an approved order', (tester) async {
    answer(Ok(approvedOrder()));
    await pumpPage(tester);
    await search(tester, 'ord-mx-000147');
    await tester.pumpAndSettle();
    expect(find.text(approvedOrderId), findsOneWidget);
    expect(
      find.bySemanticsLabel(AppStrings.statusSemantics(AppStrings.statusApproved)),
      findsOneWidget,
    );
    await tester.ensureVisible(find.byKey(OrdersKeys.grandTotal));
    await tester.pumpAndSettle();
    expect(find.bySemanticsLabel(AppStrings.grandTotalSemantics(r'$2,100.11')), findsOneWidget);
    expect(find.text(r'$2,100.11'), findsOneWidget);
    expect(find.text('Bebida 600 ml'), findsOneWidget);
    expect(find.text('PRD-008'), findsOneWidget);
    expect(find.textContaining('24 ×'), findsOneWidget);
    expect(find.text('Distribuidora Central · CLI-99821'), findsOneWidget);
    expect(find.text(AppStrings.marketMx), findsOneWidget);
    expect(find.text(AppStrings.eventVersion), findsOneWidget);
    expect(find.byKey(OrdersKeys.rejectionCard), findsNothing);
    verify(() => repository.findById(approvedOrderId)).called(1);
  });

  testWidgets('should render unit prices with four decimals', (tester) async {
    final order = approvedOrder();
    final line = OrderLine(
      productId: 'PRD-777',
      quantity: 2,
      unitPrice: UnitPrice.parse('12.3456', currency: 'MXN'),
    );
    answer(
      Ok(
        Order(
          orderId: order.orderId,
          eventVersion: order.eventVersion,
          status: order.status,
          market: order.market,
          currency: order.currency,
          client: order.client,
          lines: [line],
          totals: order.totals,
          receivedAt: order.receivedAt,
          processedAt: order.processedAt,
        ),
      ),
    );
    await pumpPage(tester);
    await search(tester, approvedOrderId);
    await tester.pumpAndSettle();
    expect(find.text(AppStrings.joinDetails(['PRD-777', r'2 × $12.3456'])), findsOneWidget);
  });

  testWidgets('should render the rejection reason and violations', (tester) async {
    answer(Ok(rejectedOrder()));
    await pumpPage(tester);
    await search(tester, rejectedOrderId);
    await tester.pumpAndSettle();
    expect(
      find.bySemanticsLabel(AppStrings.statusSemantics(AppStrings.statusRejected)),
      findsOneWidget,
    );
    expect(find.byKey(OrdersKeys.rejectionCard), findsOneWidget);
    expect(find.text('CLIENT_BLOCKED'), findsWidgets);
    expect(find.text('Producto descontinuado'), findsOneWidget);
    expect(find.text('PRODUCT_DISCONTINUED · Producto PRD-007'), findsOneWidget);
  });

  testWidgets('should render technical failure details', (tester) async {
    answer(Ok(failedOrder()));
    await pumpPage(tester);
    await search(tester, failedOrderId);
    await tester.pumpAndSettle();
    expect(find.byKey(OrdersKeys.failureCard), findsOneWidget);
    expect(find.text('DEPENDENCY_UNAVAILABLE'), findsOneWidget);
    expect(find.text('5'), findsOneWidget);
    expect(find.text(AppStrings.noLines), findsOneWidget);
  });

  testWidgets('should show the empty state when the order does not exist', (tester) async {
    answer(const Err(NotFoundFailure()));
    await pumpPage(tester);
    await search(tester, 'ORD-404');
    await tester.pumpAndSettle();
    expect(find.byKey(OrdersKeys.notFoundView), findsOneWidget);
    expect(find.text(AppStrings.notFoundMessage('ORD-404')), findsOneWidget);
  });

  testWidgets('should show an error with retry and recover on retry', (tester) async {
    answer(const Err(ServerFailure(statusCode: 503, traceId: 'trace-9')));
    await pumpPage(tester);
    await search(tester, approvedOrderId);
    await tester.pumpAndSettle();
    expect(find.byKey(OrdersKeys.failureView), findsOneWidget);
    expect(find.text(AppStrings.serverError), findsOneWidget);
    expect(find.text(AppStrings.supportCode('trace-9')), findsOneWidget);
    answer(Ok(approvedOrder()));
    await tester.tap(find.byKey(FailureView.retryButtonKey));
    await tester.pumpAndSettle();
    expect(find.byKey(OrdersKeys.detailView), findsOneWidget);
  });

  testWidgets('should not offer retry for permission errors', (tester) async {
    answer(const Err(ForbiddenFailure()));
    await pumpPage(tester);
    await search(tester, approvedOrderId);
    await tester.pumpAndSettle();
    expect(find.text(AppStrings.forbiddenError), findsOneWidget);
    expect(find.byKey(FailureView.retryButtonKey), findsNothing);
  });

  testWidgets('should explain when the api is rate limiting', (tester) async {
    answer(const Err(RateLimitedFailure(retryAfter: Duration(seconds: 4))));
    await pumpPage(tester);
    await search(tester, approvedOrderId);
    await tester.pumpAndSettle();
    expect(find.text(AppStrings.retryAfter(4)), findsOneWidget);
  });

  testWidgets('should validate the order id before searching', (tester) async {
    await pumpPage(tester);
    await search(tester, '   ');
    expect(find.text(AppStrings.orderIdRequired), findsOneWidget);
    await search(tester, 'X' * (OrderId.maxLength + 1));
    expect(find.text(AppStrings.orderIdTooLong(OrderId.maxLength)), findsOneWidget);
    verifyNever(() => repository.findById(any()));
  });

  testWidgets('should search when the keyboard action is submitted', (tester) async {
    answer(const Err(NotFoundFailure()));
    await pumpPage(tester);
    await tester.enterText(find.byKey(OrdersKeys.searchField), 'ORD-1');
    await tester.testTextInput.receiveAction(TextInputAction.search);
    await tester.pumpAndSettle();
    expect(find.byKey(OrdersKeys.notFoundView), findsOneWidget);
  });

  testWidgets('should clear the search back to idle', (tester) async {
    answer(const Err(NotFoundFailure()));
    await pumpPage(tester);
    await search(tester, 'ORD-404');
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(OrdersKeys.clearButton));
    await tester.pumpAndSettle();
    expect(find.byKey(OrdersKeys.idleView), findsOneWidget);
    expect(find.text('ORD-404'), findsNothing);
  });

  testWidgets('should use two columns for the detail on wide screens', (tester) async {
    answer(Ok(rejectedOrder()));
    await pumpPage(tester, size: desktopSize);
    await search(tester, rejectedOrderId);
    await tester.pumpAndSettle();
    final totals = tester.getTopLeft(find.byKey(OrdersKeys.totalsCard));
    final lines = tester.getTopLeft(find.byKey(OrdersKeys.linesCard));
    expect(totals.dx, greaterThan(lines.dx));
  });

  testWidgets('should stack the detail in one column on phones', (tester) async {
    answer(Ok(approvedOrder()));
    await pumpPage(tester);
    await search(tester, approvedOrderId);
    await tester.pumpAndSettle();
    final totals = tester.getTopLeft(find.byKey(OrdersKeys.totalsCard));
    final header = tester.getTopLeft(find.byKey(OrdersKeys.statusChip));
    expect(totals.dy, greaterThan(header.dy));
    expect(tester.takeException(), isNull);
  });
}
