import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:order_tracker/core/l10n/app_strings.dart';
import 'package:order_tracker/core/widgets/loading_view.dart';
import 'package:order_tracker/features/auth/domain/auth_user.dart';
import 'package:order_tracker/features/auth/presentation/auth_cubit.dart';
import 'package:order_tracker/features/auth/presentation/auth_keys.dart';
import 'package:order_tracker/features/auth/presentation/auth_state.dart';
import 'package:order_tracker/features/auth/presentation/login_page.dart';

typedef AuthenticatedBuilder = Widget Function(BuildContext context, AuthUser user);

class AuthGate extends StatelessWidget {
  const AuthGate({required this.authenticated, super.key});

  final AuthenticatedBuilder authenticated;

  @override
  Widget build(BuildContext context) {
    return BlocBuilder<AuthCubit, AuthState>(
      builder: (context, state) => switch (state) {
        AuthChecking() => const Scaffold(
          key: AuthKeys.checkingView,
          body: LoadingView(label: AppStrings.checkingSession),
        ),
        AuthRedirecting() => const LoginPage(redirecting: true),
        AuthUnauthenticated(:final failure) => LoginPage(redirecting: false, failure: failure),
        AuthAuthenticated(:final user) => authenticated(context, user),
      },
    );
  }
}
