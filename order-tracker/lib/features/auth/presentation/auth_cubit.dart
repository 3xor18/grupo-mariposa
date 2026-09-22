import 'dart:async';

import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:order_tracker/core/error/app_failure.dart';
import 'package:order_tracker/core/result/result.dart';
import 'package:order_tracker/features/auth/domain/auth_repository.dart';
import 'package:order_tracker/features/auth/domain/session_restoration.dart';
import 'package:order_tracker/features/auth/presentation/auth_state.dart';

class AuthCubit extends Cubit<AuthState> {
  AuthCubit(this._repository) : super(const AuthChecking()) {
    _expirations = _repository.sessionExpired.listen((_) => _onSessionExpired());
  }

  static const _expiredFailure = AuthenticationFailure(AuthenticationFailureReason.sessionExpired);
  static const _unexpectedFailure = AuthenticationFailure(AuthenticationFailureReason.unexpected);

  final AuthRepository _repository;
  late final StreamSubscription<void> _expirations;

  Future<void> initialize() async {
    final Result<SessionRestoration> result;
    try {
      result = await _repository.restoreSession();
    } on Exception {
      _emitIfOpen(const AuthUnauthenticated(failure: _unexpectedFailure));
      return;
    }
    switch (result) {
      case Ok<SessionRestoration>(value: SignedIn(:final user)):
        _emitIfOpen(AuthAuthenticated(user));
      case Ok<SessionRestoration>(value: SignedOut()):
        _emitIfOpen(const AuthUnauthenticated());
      case Ok<SessionRestoration>(value: SigningInSilently()):
        return;
      case Err<SessionRestoration>(:final failure):
        _emitIfOpen(AuthUnauthenticated(failure: failure));
    }
  }

  Future<void> login() async {
    emit(const AuthRedirecting());
    await _repository.login();
  }

  Future<void> logout() async {
    emit(const AuthRedirecting());
    await _repository.logout();
  }

  void _onSessionExpired() => _emitIfOpen(const AuthUnauthenticated(failure: _expiredFailure));

  void _emitIfOpen(AuthState state) {
    if (!isClosed) {
      emit(state);
    }
  }

  @override
  Future<void> close() async {
    await _expirations.cancel();
    return super.close();
  }
}
