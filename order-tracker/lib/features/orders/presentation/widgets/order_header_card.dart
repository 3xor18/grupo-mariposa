import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:order_tracker/core/format/app_formatters.dart';
import 'package:order_tracker/core/l10n/app_strings.dart';
import 'package:order_tracker/core/theme/app_tokens.dart';
import 'package:order_tracker/features/orders/domain/entities/order.dart';
import 'package:order_tracker/features/orders/presentation/orders_keys.dart';
import 'package:order_tracker/features/orders/presentation/widgets/labeled_value.dart';
import 'package:order_tracker/features/orders/presentation/widgets/order_labels.dart';
import 'package:order_tracker/features/orders/presentation/widgets/order_status_chip.dart';

class OrderHeaderCard extends StatelessWidget {
  const OrderHeaderCard({required this.order, super.key});

  final Order order;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Card.outlined(
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.md),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Wrap(
              spacing: AppSpacing.md,
              runSpacing: AppSpacing.sm,
              crossAxisAlignment: WrapCrossAlignment.center,
              children: [
                Semantics(
                  header: true,
                  child: SelectableText(order.orderId, style: theme.textTheme.headlineSmall),
                ),
                OrderStatusChip(key: OrdersKeys.statusChip, status: order.status),
              ],
            ),
            const SizedBox(height: AppSpacing.lg),
            Wrap(
              spacing: AppSpacing.xl,
              runSpacing: AppSpacing.md,
              children: _facts(context.read<AppFormatters>()),
            ),
          ],
        ),
      ),
    );
  }

  List<Widget> _facts(AppFormatters formatters) {
    final client = order.client;
    final clientName = client.name;
    final channel = order.channel;
    final segment = client.segment;
    final occurredAt = order.occurredAt;
    return [
      LabeledValue(
        label: AppStrings.client,
        value: clientName == null
            ? client.clientId
            : AppStrings.clientWithId(clientName, client.clientId),
      ),
      if (segment != null) LabeledValue(label: AppStrings.segment, value: segment),
      LabeledValue(label: AppStrings.market, value: order.market.label),
      LabeledValue(label: AppStrings.eventVersion, value: '${order.eventVersion}'),
      if (channel != null) LabeledValue(label: AppStrings.channel, value: channel),
      if (occurredAt != null)
        LabeledValue(label: AppStrings.occurredAt, value: formatters.dateTime(occurredAt)),
      LabeledValue(label: AppStrings.receivedAt, value: formatters.dateTime(order.receivedAt)),
      LabeledValue(label: AppStrings.processedAt, value: formatters.dateTime(order.processedAt)),
    ];
  }
}
