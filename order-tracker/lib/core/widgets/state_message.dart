import 'package:flutter/material.dart';
import 'package:order_tracker/core/theme/app_tokens.dart';

class StateMessage extends StatelessWidget {
  const StateMessage({
    required this.icon,
    required this.title,
    required this.message,
    this.detail,
    this.action,
    this.iconColor,
    super.key,
  });

  final IconData icon;
  final String title;
  final String message;
  final String? detail;
  final Widget? action;
  final Color? iconColor;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final detail = this.detail;
    final action = this.action;
    return Center(
      child: SingleChildScrollView(
        padding: const EdgeInsets.all(AppSpacing.lg),
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: AppSizes.maxFormWidth),
          child: Semantics(
            container: true,
            liveRegion: true,
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Icon(
                  icon,
                  size: AppSizes.emptyStateIcon,
                  color: iconColor ?? theme.colorScheme.primary,
                ),
                const SizedBox(height: AppSpacing.md),
                Semantics(
                  container: true,
                  header: true,
                  child: Text(
                    title,
                    style: theme.textTheme.titleLarge,
                    textAlign: TextAlign.center,
                  ),
                ),
                const SizedBox(height: AppSpacing.sm),
                Text(message, style: theme.textTheme.bodyMedium, textAlign: TextAlign.center),
                if (detail != null) ...[
                  const SizedBox(height: AppSpacing.sm),
                  SelectableText(
                    detail,
                    style: theme.textTheme.bodySmall,
                    textAlign: TextAlign.center,
                  ),
                ],
                if (action != null) ...[const SizedBox(height: AppSpacing.lg), action],
              ],
            ),
          ),
        ),
      ),
    );
  }
}
