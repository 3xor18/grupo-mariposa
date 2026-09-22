import 'dart:async';

import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:order_tracker/core/error/app_failure.dart';
import 'package:order_tracker/core/result/result.dart';
import 'package:order_tracker/features/auth/domain/auth_repository.dart';
import 'package:order_tracker/features/auth/domain/auth_user.dart';
import 'package:order_tracker/features/auth/presentation/auth_state.dart';

class AuthCubit extends Cubit<AuthState> {
  AuthCubit(this._repository) : super(const AuthChecking()) {
    _expirations = _repository.sessionExpired.listen((_) => _onSessionExpired());
  }

  static const _expiredFailure = AuthenticationFailure(AuthenticationFailureReason.sessionExpired);

  final AuthRepository _repository;
  late final StreamSubscription<void> _expirations;

  Future<void> initialize() async {
    final result = await _repository.restoreSession();
    emit(switch (result) {
      Ok<AuthUser?>(value: final AuthUser user) => AuthAuthenticated(user),
      Ok<AuthUser?>() => const AuthUnauthenticated(),
      Err<AuthUser?>(:final failure) => AuthUnauthenticated(failure: failure),
    });
  }

  Future<void> login() async {
    emit(const AuthRedirecting());
    await _repository.login();
  }

  Future<void> logout() async {
    emit(const AuthRedirecting());
    await _repository.logout();
  }

  void _onSessionExpired() => emit(const AuthUnauthenticated(failure: _expiredFailure));

  @override
  Future<void> close() async {
    await _expirations.cancel();
    return super.close();
  }
}
