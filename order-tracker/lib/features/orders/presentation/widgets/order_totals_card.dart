import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:order_tracker/core/format/app_formatters.dart';
import 'package:order_tracker/core/l10n/app_strings.dart';
import 'package:order_tracker/core/theme/app_tokens.dart';
import 'package:order_tracker/features/orders/domain/entities/order.dart';
import 'package:order_tracker/features/orders/presentation/orders_keys.dart';
import 'package:order_tracker/features/orders/presentation/widgets/labeled_value.dart';

class OrderTotalsCard extends StatelessWidget {
  const OrderTotalsCard({required this.totals, super.key});

  final OrderTotals totals;

  @override
  Widget build(BuildContext context) {
    final money = context.read<AppFormatters>().money;
    final grandTotal = money(totals.grandTotal);
    return SectionCard(
      title: AppStrings.totals,
      icon: Icons.receipt_long_outlined,
      children: [
        _TotalRow(label: AppStrings.grossSubtotal, value: money(totals.grossSubtotal)),
        _TotalRow(label: AppStrings.discount, value: money(totals.discount)),
        _TotalRow(label: AppStrings.netSubtotal, value: money(totals.netSubtotal)),
        _TotalRow(label: AppStrings.tax, value: money(totals.tax)),
        const Divider(height: AppSpacing.lg),
        Semantics(
          key: OrdersKeys.grandTotal,
          container: true,
          label: AppStrings.grandTotalSemantics(grandTotal),
          excludeSemantics: true,
          child: _TotalRow(label: AppStrings.grandTotal, value: grandTotal, emphasized: true),
        ),
      ],
    );
  }
}

class _TotalRow extends StatelessWidget {
  const _TotalRow({required this.label, required this.value, this.emphasized = false});

  final String label;
  final String value;
  final bool emphasized;

  @override
  Widget build(BuildContext context) {
    final textTheme = Theme.of(context).textTheme;
    final style = emphasized ? textTheme.titleLarge : textTheme.bodyLarge;
    return MergeSemantics(
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: AppSpacing.xs),
        child: Row(
          children: [
            Expanded(child: Text(label, style: style)),
            Text(value, style: style),
          ],
        ),
      ),
    );
  }
}
