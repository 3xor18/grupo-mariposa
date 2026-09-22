import 'package:flutter/material.dart';
import 'package:order_tracker/core/l10n/app_strings.dart';
import 'package:order_tracker/core/theme/app_tokens.dart';
import 'package:order_tracker/core/theme/status_palette.dart';
import 'package:order_tracker/features/orders/domain/entities/order.dart';
import 'package:order_tracker/features/orders/presentation/widgets/labeled_value.dart';

class OrderRejectionCard extends StatelessWidget {
  const OrderRejectionCard({required this.reason, required this.violations, super.key});

  final String? reason;
  final List<Violation> violations;

  @override
  Widget build(BuildContext context) {
    final tone = Theme.of(context).extension<StatusPalette>()!.rejected;
    final reason = this.reason;
    return SectionCard(
      title: AppStrings.rejectionTitle,
      icon: Icons.block,
      color: tone.container,
      onColor: tone.onContainer,
      children: [
        if (reason != null) Text(reason, style: TextStyle(color: tone.onContainer)),
        if (violations.isNotEmpty) ...[
          const SizedBox(height: AppSpacing.md),
          Text(AppStrings.violations, style: TextStyle(color: tone.onContainer)),
          for (final violation in violations)
            _ViolationTile(violation: violation, color: tone.onContainer),
        ],
      ],
    );
  }
}

class _ViolationTile extends StatelessWidget {
  const _ViolationTile({required this.violation, required this.color});

  final Violation violation;
  final Color color;

  @override
  Widget build(BuildContext context) {
    final productId = violation.productId;
    return ListTile(
      contentPadding: EdgeInsets.zero,
      textColor: color,
      iconColor: color,
      leading: const Icon(Icons.rule),
      title: Text(violation.message),
      subtitle: Text(
        AppStrings.joinDetails([
          violation.code,
          if (productId != null) AppStrings.productReference(productId),
        ]),
      ),
    );
  }
}

class OrderFailureCard extends StatelessWidget {
  const OrderFailureCard({required this.failure, super.key});

  final ProcessingFailure failure;

  @override
  Widget build(BuildContext context) {
    final tone = Theme.of(context).extension<StatusPalette>()!.failure;
    return SectionCard(
      title: AppStrings.failureTitle,
      icon: Icons.warning_amber_outlined,
      color: tone.container,
      onColor: tone.onContainer,
      children: [
        Wrap(
          spacing: AppSpacing.xl,
          runSpacing: AppSpacing.md,
          children: [
            LabeledValue(label: AppStrings.failureCategory, value: failure.category),
            LabeledValue(label: AppStrings.failureCause, value: failure.cause),
            LabeledValue(label: AppStrings.failureAttempts, value: '${failure.attempts}'),
          ],
        ),
        const SizedBox(height: AppSpacing.md),
        Text(AppStrings.failureHint, style: TextStyle(color: tone.onContainer)),
      ],
    );
  }
}
