import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:order_tracker/core/format/app_formatters.dart';
import 'package:order_tracker/core/l10n/app_strings.dart';
import 'package:order_tracker/core/theme/app_tokens.dart';
import 'package:order_tracker/features/orders/domain/entities/order_summary.dart';
import 'package:order_tracker/features/orders/presentation/orders_keys.dart';
import 'package:order_tracker/features/orders/presentation/widgets/order_labels.dart';
import 'package:order_tracker/features/orders/presentation/widgets/order_status_chip.dart';

class OrderSummaryTile extends StatelessWidget {
  const OrderSummaryTile({
    required this.summary,
    required this.selected,
    required this.onTap,
    super.key,
  });

  final OrderSummary summary;
  final bool selected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final formatters = context.read<AppFormatters>();
    final total = formatters.money(summary.grandTotal);
    return Semantics(
      container: true,
      label: AppStrings.orderSemantics(summary.orderId, summary.status.label, total),
      button: true,
      onTap: onTap,
      selected: selected,
      excludeSemantics: true,
      child: ListTile(
        key: OrdersKeys.summaryTile(summary.orderId),
        selected: selected,
        onTap: onTap,
        title: Text(summary.orderId),
        subtitle: Text(
          AppStrings.joinDetails([
            summary.clientId,
            summary.market.label,
            formatters.dateTime(summary.processedAt),
          ]),
        ),
        trailing: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          crossAxisAlignment: CrossAxisAlignment.end,
          spacing: AppSpacing.xs,
          children: [
            Text(total, style: Theme.of(context).textTheme.titleSmall),
            OrderStatusChip(status: summary.status, compact: true),
          ],
        ),
      ),
    );
  }
}
