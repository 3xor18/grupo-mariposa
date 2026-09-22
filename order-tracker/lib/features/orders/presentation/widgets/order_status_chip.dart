import 'package:flutter/material.dart';
import 'package:order_tracker/core/l10n/app_strings.dart';
import 'package:order_tracker/core/theme/app_tokens.dart';
import 'package:order_tracker/core/theme/status_palette.dart';
import 'package:order_tracker/features/orders/domain/entities/order_status.dart';
import 'package:order_tracker/features/orders/presentation/widgets/order_labels.dart';

class OrderStatusChip extends StatelessWidget {
  const OrderStatusChip({required this.status, this.compact = false, super.key});

  final OrderStatus status;
  final bool compact;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final tone = status.toneIn(context.statusPalette);
    final textStyle = compact ? theme.textTheme.labelSmall : theme.textTheme.labelLarge;
    return Semantics(
      container: true,
      label: AppStrings.statusSemantics(status.label),
      excludeSemantics: true,
      child: DecoratedBox(
        decoration: BoxDecoration(
          color: tone.container,
          borderRadius: BorderRadius.circular(AppSizes.badgeRadius),
        ),
        child: Padding(
          padding: EdgeInsets.symmetric(
            horizontal: compact ? AppSpacing.sm : AppSpacing.md,
            vertical: compact ? AppSpacing.xxs : AppSpacing.sm,
          ),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(
                status.icon,
                size: compact ? AppSizes.compactBadgeIcon : AppSizes.badgeIcon,
                color: tone.onContainer,
              ),
              const SizedBox(width: AppSpacing.xs),
              Text(status.label, style: textStyle?.copyWith(color: tone.onContainer)),
            ],
          ),
        ),
      ),
    );
  }
}
