import 'package:equatable/equatable.dart';
import 'package:order_tracker/core/error/app_failure.dart';
import 'package:order_tracker/features/auth/domain/auth_user.dart';

sealed class AuthState extends Equatable {
  const AuthState();

  @override
  List<Object?> get props => [];
}

final class AuthChecking extends AuthState {
  const AuthChecking();
}

final class AuthRedirecting extends AuthState {
  const AuthRedirecting();
}

final class AuthUnauthenticated extends AuthState {
  const AuthUnauthenticated({this.failure});

  final AppFailure? failure;

  @override
  List<Object?> get props => [failure];
}

final class AuthAuthenticated extends AuthState {
  const AuthAuthenticated(this.user);

  final AuthUser user;

  @override
  List<Object?> get props => [user];
}
