import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:order_tracker/core/error/app_failure.dart';
import 'package:order_tracker/core/l10n/app_strings.dart';
import 'package:order_tracker/core/theme/app_tokens.dart';
import 'package:order_tracker/core/widgets/failure_messages.dart';
import 'package:order_tracker/features/auth/presentation/auth_cubit.dart';
import 'package:order_tracker/features/auth/presentation/auth_keys.dart';

class LoginPage extends StatelessWidget {
  const LoginPage({required this.redirecting, this.failure, super.key});

  final bool redirecting;
  final AppFailure? failure;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final failure = this.failure;
    return Scaffold(
      key: AuthKeys.loginPage,
      body: Center(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(AppSpacing.lg),
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: AppSizes.maxFormWidth),
            child: Card.outlined(
              child: Padding(
                padding: const EdgeInsets.all(AppSpacing.xl),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  spacing: AppSpacing.md,
                  children: [
                    Icon(
                      Icons.local_shipping_outlined,
                      size: AppSizes.brandIcon,
                      color: theme.colorScheme.primary,
                    ),
                    Semantics(
                      header: true,
                      child: Text(
                        AppStrings.appTitle,
                        style: theme.textTheme.headlineSmall,
                        textAlign: TextAlign.center,
                      ),
                    ),
                    Text(
                      AppStrings.loginSubtitle,
                      style: theme.textTheme.bodyMedium,
                      textAlign: TextAlign.center,
                    ),
                    if (failure != null) _LoginError(failure: failure),
                    _LoginButton(redirecting: redirecting),
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class _LoginError extends StatelessWidget {
  const _LoginError({required this.failure});

  final AppFailure failure;

  @override
  Widget build(BuildContext context) {
    final colors = Theme.of(context).colorScheme;
    return Semantics(
      liveRegion: true,
      child: Card.filled(
        key: AuthKeys.loginError,
        color: colors.errorContainer,
        child: Padding(
          padding: const EdgeInsets.all(AppSpacing.md),
          child: Text(
            FailureMessages.of(failure),
            style: TextStyle(color: colors.onErrorContainer),
          ),
        ),
      ),
    );
  }
}

class _LoginButton extends StatelessWidget {
  const _LoginButton({required this.redirecting});

  final bool redirecting;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: double.infinity,
      child: FilledButton.icon(
        key: AuthKeys.loginButton,
        onPressed: redirecting ? null : () => context.read<AuthCubit>().login(),
        icon: redirecting
            ? const SizedBox.square(
                dimension: AppSizes.progressIndicator,
                child: CircularProgressIndicator(strokeWidth: AppSizes.progressStroke),
              )
            : const Icon(Icons.login),
        label: const Text(AppStrings.loginButton),
      ),
    );
  }
}
