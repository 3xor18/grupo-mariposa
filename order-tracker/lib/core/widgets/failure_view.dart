import 'package:flutter/material.dart';
import 'package:order_tracker/core/error/app_failure.dart';
import 'package:order_tracker/core/l10n/app_strings.dart';
import 'package:order_tracker/core/widgets/failure_messages.dart';
import 'package:order_tracker/core/widgets/state_message.dart';

class FailureView extends StatelessWidget {
  const FailureView({required this.failure, required this.onRetry, super.key});

  static const Key retryButtonKey = ValueKey('failureRetryButton');

  final AppFailure failure;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    return StateMessage(
      icon: Icons.cloud_off_outlined,
      iconColor: Theme.of(context).colorScheme.error,
      title: AppStrings.errorTitle,
      message: FailureMessages.of(failure),
      detail: FailureMessages.detailOf(failure),
      action: failure.isRetryable
          ? FilledButton.tonalIcon(
              key: retryButtonKey,
              onPressed: onRetry,
              icon: const Icon(Icons.refresh),
              label: const Text(AppStrings.retry),
            )
          : null,
    );
  }
}
