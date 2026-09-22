import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:order_tracker/core/l10n/app_strings.dart';
import 'package:order_tracker/core/theme/app_theme.dart';
import 'package:order_tracker/core/theme/status_palette.dart';
import 'package:order_tracker/features/auth/data/authorization_callback.dart';
import 'package:order_tracker/features/auth/domain/session_restoration.dart';
import 'package:order_tracker/features/orders/domain/entities/market.dart';
import 'package:order_tracker/features/orders/domain/entities/order.dart';
import 'package:order_tracker/features/orders/domain/entities/order_status.dart';
import 'package:order_tracker/features/orders/presentation/list/orders_list_event.dart';
import 'package:order_tracker/features/orders/presentation/search/order_search_event.dart';
import 'package:order_tracker/features/orders/presentation/widgets/order_labels.dart';
import 'package:order_tracker/features/orders/presentation/widgets/order_status_chip.dart';

void main() {
  group('status presentation', () {
    final palette = AppTheme.light().extension<StatusPalette>()!;

    test('should label, iconize and color every status', () {
      expect(OrderStatus.values.map((status) => status.label), [
        AppStrings.statusApproved,
        AppStrings.statusRejected,
        AppStrings.statusTechnicalFailure,
        AppStrings.statusUnknown,
      ]);
      expect(OrderStatus.unknown.icon, Icons.help_outline);
      expect(OrderStatus.unknown.toneIn(palette), palette.neutral);
      expect(OrderStatus.technicalFailure.toneIn(palette), palette.failure);
    });

    test('should label every market', () {
      expect(Market.values.map((market) => market.label), [
        AppStrings.marketMx,
        AppStrings.marketCo,
        AppStrings.marketPe,
        AppStrings.marketUnknown,
      ]);
    });

    testWidgets('should announce unknown statuses', (tester) async {
      await tester.pumpWidget(
        MaterialApp(
          theme: AppTheme.light(),
          home: const OrderStatusChip(status: OrderStatus.unknown, compact: true),
        ),
      );
      expect(find.bySemanticsLabel('Estado: Desconocido'), findsOneWidget);
    });
  });

  group('value semantics', () {
    test('should compare events by value', () {
      expect(const OrderSearchSubmitted('A').props, ['A']);
      expect(const OrderSearchRetried().props, isEmpty);
      expect(const OrderSearchCleared().props, isEmpty);
      expect(const OrdersListRequested().props, isEmpty);
      expect(const OrdersListRefreshed().props, isEmpty);
      expect(const OrdersListNextPageRequested().props, isEmpty);
      expect(const OrdersListStatusToggled(OrderStatus.approved).props, [OrderStatus.approved]);
      expect(const OrdersListMarketToggled(Market.pe).props, [Market.pe]);
    });

    test('should compare processing failures and callbacks by value', () {
      const failure = ProcessingFailure(category: 'C', cause: 'x', attempts: 1);
      expect(failure.props, ['C', 'x', 1]);
      expect(const NoAuthorizationCallback().props, isEmpty);
      expect(const SignedOut().props, isEmpty);
      expect(const SigningInSilently(), const SigningInSilently());
    });
  });
}
