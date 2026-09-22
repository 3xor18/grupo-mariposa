import 'dart:async';
import 'dart:convert';

import 'package:order_tracker/core/error/app_failure.dart';
import 'package:order_tracker/core/http/access_token_provider.dart';
import 'package:order_tracker/core/json/json_map.dart';
import 'package:order_tracker/core/platform/browser_location.dart';
import 'package:order_tracker/core/result/result.dart';
import 'package:order_tracker/core/time/clock.dart';
import 'package:order_tracker/features/auth/data/authorization_callback.dart';
import 'package:order_tracker/features/auth/data/jwt_claims.dart';
import 'package:order_tracker/features/auth/data/oidc_client.dart';
import 'package:order_tracker/features/auth/data/oidc_endpoints.dart';
import 'package:order_tracker/features/auth/data/pkce.dart';
import 'package:order_tracker/features/auth/data/token_set.dart';
import 'package:order_tracker/features/auth/domain/auth_repository.dart';
import 'package:order_tracker/features/auth/domain/auth_user.dart';

abstract final class AuthStorageKeys {
  static const tokens = 'order-tracker.auth.tokens';
  static const verifier = 'order-tracker.auth.pkce-verifier';
  static const state = 'order-tracker.auth.state';
}

final class AuthRepositoryImpl implements AuthRepository, AccessTokenProvider {
  AuthRepositoryImpl({
    required this._oidcClient,
    required this._endpoints,
    required this._store,
    required this._location,
    PkceGenerator? pkce,
    this._clock = systemClock,
  }) : _pkce = pkce ?? PkceGenerator();

  static const refreshMargin = Duration(seconds: 30);

  final OidcClient _oidcClient;
  final OidcEndpoints _endpoints;
  final KeyValueStore _store;
  final BrowserLocation _location;
  final PkceGenerator _pkce;
  final Clock _clock;
  final StreamController<void> _expired = StreamController<void>.broadcast();

  TokenSet? _tokens;
  Future<TokenSet?>? _pendingRefresh;

  Uri get _redirectUri => AuthorizationCallback.stripFrom(_location.current);

  @override
  Stream<void> get sessionExpired => _expired.stream;

  @override
  Future<Result<AuthUser?>> restoreSession() async {
    return switch (AuthorizationCallback.parse(_location.current)) {
      final AuthorizationGranted granted => _completeLogin(granted),
      AuthorizationDenied() => _rejectCallback(),
      NoAuthorizationCallback() => _restoreStoredSession(),
    };
  }

  @override
  Future<void> login() async {
    final pair = _pkce.generate();
    final state = _pkce.randomToken(PkceGenerator.stateByteLength);
    _store
      ..write(AuthStorageKeys.verifier, pair.verifier)
      ..write(AuthStorageKeys.state, state);
    _location.assign(
      _endpoints.authorization(
        redirectUri: _redirectUri,
        state: state,
        codeChallenge: pair.challenge,
      ),
    );
  }

  @override
  Future<void> logout() async {
    final idToken = _tokens?.idToken;
    _clearSession();
    _location.assign(_endpoints.logout(redirectUri: _redirectUri, idTokenHint: idToken));
  }

  @override
  Future<String?> validAccessToken() async {
    final hadSession = _tokens != null;
    final tokens = await _freshTokens();
    if (hadSession && tokens == null) {
      _expired.add(null);
    }
    return tokens?.accessToken;
  }

  @override
  void onUnauthorized() {
    if (_tokens == null) {
      return;
    }
    _clearSession();
    _expired.add(null);
  }

  Future<void> dispose() => _expired.close();

  Future<Result<AuthUser?>> _completeLogin(AuthorizationGranted granted) async {
    final expectedState = _store.read(AuthStorageKeys.state);
    final verifier = _store.read(AuthStorageKeys.verifier);
    final redirectUri = _redirectUri;
    _forgetLoginAttempt();
    _location.replace(redirectUri);
    if (verifier == null || expectedState == null || expectedState != granted.state) {
      return const Result.err(AuthenticationFailure(AuthenticationFailureReason.stateMismatch));
    }
    final result = await _oidcClient.exchangeCode(
      code: granted.code,
      verifier: verifier,
      redirectUri: redirectUri,
    );
    return result.map((tokens) => _userOf(_saveSession(tokens)));
  }

  Future<Result<AuthUser?>> _rejectCallback() async {
    _forgetLoginAttempt();
    _location.replace(_redirectUri);
    return const Result.err(AuthenticationFailure(AuthenticationFailureReason.callbackRejected));
  }

  Future<Result<AuthUser?>> _restoreStoredSession() async {
    _tokens = _readStoredSession();
    final tokens = await _freshTokens();
    return Result.ok(tokens == null ? null : _userOf(tokens));
  }

  Future<TokenSet?> _freshTokens() {
    final tokens = _tokens;
    if (tokens == null || !tokens.expiresWithin(_clock(), refreshMargin)) {
      return Future.value(tokens);
    }
    return _pendingRefresh ??= _refresh(tokens).whenComplete(() => _pendingRefresh = null);
  }

  Future<TokenSet?> _refresh(TokenSet tokens) async {
    final refreshToken = tokens.refreshToken;
    if (refreshToken == null || !tokens.canRefreshAt(_clock())) {
      return _endSession();
    }
    final result = await _oidcClient.refresh(refreshToken);
    return switch (result) {
      Ok<TokenSet>(:final value) => _saveSession(tokens.mergedWith(value)),
      Err<TokenSet>() => _endSession(),
    };
  }

  TokenSet? _endSession() {
    _clearSession();
    return null;
  }

  TokenSet _saveSession(TokenSet tokens) {
    _tokens = tokens;
    _store.write(AuthStorageKeys.tokens, jsonEncode(tokens.toStorage()));
    return tokens;
  }

  TokenSet? _readStoredSession() {
    final raw = _store.read(AuthStorageKeys.tokens);
    if (raw == null) {
      return null;
    }
    try {
      return TokenSet.fromStorage(JsonMap.parse(jsonDecode(raw)));
    } on FormatException {
      _store.remove(AuthStorageKeys.tokens);
      return null;
    }
  }

  void _clearSession() {
    _tokens = null;
    _store.remove(AuthStorageKeys.tokens);
  }

  void _forgetLoginAttempt() {
    _store
      ..remove(AuthStorageKeys.verifier)
      ..remove(AuthStorageKeys.state);
  }

  AuthUser _userOf(TokenSet tokens) => JwtClaims.userFrom(tokens.idToken ?? tokens.accessToken);
}
