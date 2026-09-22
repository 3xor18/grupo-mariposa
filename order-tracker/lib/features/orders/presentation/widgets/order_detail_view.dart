import 'package:flutter/material.dart';
import 'package:order_tracker/core/theme/app_tokens.dart';
import 'package:order_tracker/features/orders/domain/entities/order.dart';
import 'package:order_tracker/features/orders/presentation/orders_keys.dart';
import 'package:order_tracker/features/orders/presentation/widgets/order_header_card.dart';
import 'package:order_tracker/features/orders/presentation/widgets/order_lines_card.dart';
import 'package:order_tracker/features/orders/presentation/widgets/order_outcome_cards.dart';
import 'package:order_tracker/features/orders/presentation/widgets/order_totals_card.dart';

class OrderDetailView extends StatelessWidget {
  const OrderDetailView({required this.order, super.key});

  final Order order;

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) {
        final wide = constraints.maxWidth >= AppBreakpoints.expanded;
        return SingleChildScrollView(
          key: OrdersKeys.detailView,
          padding: const EdgeInsets.all(AppSpacing.md),
          child: wide ? _twoColumns() : _singleColumn(),
        );
      },
    );
  }

  Widget _singleColumn() {
    return _Stack(
      children: [
        OrderHeaderCard(order: order),
        ..._outcome(),
        _totals(),
        _lines(),
      ],
    );
  }

  Widget _twoColumns() {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Expanded(
          flex: AppFlex.detailMain,
          child: _Stack(
            children: [
              OrderHeaderCard(order: order),
              _lines(),
            ],
          ),
        ),
        const SizedBox(width: AppSpacing.md),
        Expanded(
          flex: AppFlex.detailSide,
          child: _Stack(children: [_totals(), ..._outcome()]),
        ),
      ],
    );
  }

  List<Widget> _outcome() {
    final failure = order.failure;
    return [
      if (order.isRejected || order.violations.isNotEmpty)
        OrderRejectionCard(
          key: OrdersKeys.rejectionCard,
          reason: order.reason,
          violations: order.violations,
        ),
      if (failure != null) OrderFailureCard(key: OrdersKeys.failureCard, failure: failure),
    ];
  }

  Widget _totals() {
    return OrderTotalsCard(
      key: OrdersKeys.totalsCard,
      totals: order.totals,
    );
  }

  Widget _lines() {
    return OrderLinesCard(key: OrdersKeys.linesCard, lines: order.lines);
  }
}

class _Stack extends StatelessWidget {
  const _Stack({required this.children});

  final List<Widget> children;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      spacing: AppSpacing.md,
      children: children,
    );
  }
}
