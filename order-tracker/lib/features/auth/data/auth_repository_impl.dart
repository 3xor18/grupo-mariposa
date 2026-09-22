import 'dart:async';

import 'package:order_tracker/core/error/app_failure.dart';
import 'package:order_tracker/core/http/access_token_provider.dart';
import 'package:order_tracker/core/platform/browser_location.dart';
import 'package:order_tracker/core/platform/key_value_store.dart';
import 'package:order_tracker/core/result/result.dart';
import 'package:order_tracker/core/time/clock.dart';
import 'package:order_tracker/features/auth/data/auth_storage_keys.dart';
import 'package:order_tracker/features/auth/data/authorization_callback.dart';
import 'package:order_tracker/features/auth/data/id_token_validator.dart';
import 'package:order_tracker/features/auth/data/jwt_claims.dart';
import 'package:order_tracker/features/auth/data/oidc_client.dart';
import 'package:order_tracker/features/auth/data/oidc_endpoints.dart';
import 'package:order_tracker/features/auth/data/pkce.dart';
import 'package:order_tracker/features/auth/data/token_set.dart';
import 'package:order_tracker/features/auth/domain/auth_repository.dart';
import 'package:order_tracker/features/auth/domain/auth_user.dart';
import 'package:order_tracker/features/auth/domain/session_restoration.dart';

final class AuthRepositoryImpl implements AuthRepository, AccessTokenProvider {
  AuthRepositoryImpl({
    required this._oidcClient,
    required this._endpoints,
    required this._redirectUri,
    required this._store,
    required this._location,
    PkceGenerator? pkce,
    this._clock = systemClock,
  }) : _pkce = pkce ?? PkceGenerator(),
       _idTokens = IdTokenValidator(issuer: _endpoints.issuer, clientId: _endpoints.clientId);

  static const refreshMargin = Duration(seconds: 30);

  final OidcClient _oidcClient;
  final OidcEndpoints _endpoints;
  final Uri _redirectUri;
  final KeyValueStore _store;
  final BrowserLocation _location;
  final PkceGenerator _pkce;
  final Clock _clock;
  final IdTokenValidator _idTokens;
  final StreamController<void> _expired = StreamController<void>.broadcast();

  TokenSet? _tokens;
  int _epoch = 0;
  Future<TokenSet?>? _pendingRefresh;

  @override
  Stream<void> get sessionExpired => _expired.stream;

  @override
  Future<Result<SessionRestoration>> restoreSession() async {
    return switch (AuthorizationCallback.parse(_location.current)) {
      final AuthorizationGranted granted => _completeSignIn(granted),
      AuthorizationDenied() => _handleDeniedCallback(),
      NoAuthorizationCallback() => _restoreOrSignInSilently(),
    };
  }

  @override
  Future<void> login() async => _redirectToAuthorization(silent: false);

  @override
  Future<void> logout() async {
    final idToken = _tokens?.idToken;
    _endSession();
    _location.assign(_endpoints.logout(redirectUri: _redirectUri, idTokenHint: idToken));
  }

  @override
  Future<String?> validAccessToken() async {
    final tokens = _tokens;
    if (tokens == null) {
      return null;
    }
    if (!tokens.expiresWithin(_clock(), refreshMargin)) {
      return tokens.accessToken;
    }
    final refreshed = await _sharedRefresh(tokens);
    return refreshed?.accessToken;
  }

  @override
  void onUnauthorized(String? rejectedToken) {
    final current = _tokens?.accessToken;
    if (rejectedToken != null && rejectedToken == current) {
      _expireSession();
    }
  }

  Future<void> dispose() => _expired.close();

  Result<SessionRestoration> _restoreOrSignInSilently() {
    if (_tokens case final TokenSet tokens) {
      return Result.ok(SignedIn(_userOf(tokens)));
    }
    _redirectToAuthorization(silent: true);
    return const Result.ok(SigningInSilently());
  }

  void _redirectToAuthorization({required bool silent}) {
    final pair = _pkce.generate();
    final state = _pkce.randomToken(PkceGenerator.stateByteLength);
    final nonce = _pkce.randomToken(PkceGenerator.stateByteLength);
    _store
      ..write(AuthStorageKeys.verifier, pair.verifier)
      ..write(AuthStorageKeys.state, state)
      ..write(AuthStorageKeys.nonce, nonce)
      ..write(
        AuthStorageKeys.mode,
        silent ? AuthRedirectModes.silent : AuthRedirectModes.interactive,
      );
    _location.assign(
      _endpoints.authorization(
        redirectUri: _redirectUri,
        state: state,
        codeChallenge: pair.challenge,
        nonce: nonce,
        prompt: silent ? OidcValues.silentPrompt : null,
      ),
    );
  }

  Result<SessionRestoration> _handleDeniedCallback() {
    final silent = _store.read(AuthStorageKeys.mode) == AuthRedirectModes.silent;
    _forgetLoginAttempt();
    _location.replace(_redirectUri);
    if (silent) {
      return const Result.ok(SignedOut());
    }
    return const Result.err(AuthenticationFailure(AuthenticationFailureReason.callbackRejected));
  }

  Future<Result<SessionRestoration>> _completeSignIn(AuthorizationGranted granted) async {
    final expectedState = _store.read(AuthStorageKeys.state);
    final verifier = _store.read(AuthStorageKeys.verifier);
    final nonce = _store.read(AuthStorageKeys.nonce);
    _forgetLoginAttempt();
    _location.replace(_redirectUri);
    if (verifier == null || nonce == null || expectedState != granted.state) {
      return const Result.err(AuthenticationFailure(AuthenticationFailureReason.stateMismatch));
    }
    final result = await _oidcClient.exchangeCode(
      code: granted.code,
      verifier: verifier,
      redirectUri: _redirectUri,
    );
    return switch (result) {
      Ok<TokenSet>(:final value) when _hasValidIdToken(value, nonce: nonce) => Result.ok(
        SignedIn(_userOf(_startSession(value))),
      ),
      Ok<TokenSet>() => const Result.err(
        AuthenticationFailure(AuthenticationFailureReason.invalidIdToken),
      ),
      Err<TokenSet>(:final failure) => Result.err(failure),
    };
  }

  bool _hasValidIdToken(TokenSet tokens, {String? nonce}) {
    return switch (tokens.idToken) {
      final String idToken => _idTokens.isValid(idToken, now: _clock(), expectedNonce: nonce),
      null => nonce == null,
    };
  }

  Future<TokenSet?> _sharedRefresh(TokenSet tokens) {
    if (_pendingRefresh case final Future<TokenSet?> pending) {
      return pending;
    }
    late final Future<TokenSet?> refresh;
    refresh = _refresh(tokens).whenComplete(() {
      if (identical(_pendingRefresh, refresh)) {
        _pendingRefresh = null;
      }
    });
    return _pendingRefresh = refresh;
  }

  Future<TokenSet?> _refresh(TokenSet tokens) async {
    final epoch = _epoch;
    final refreshToken = tokens.refreshToken;
    if (refreshToken == null || !tokens.canRefreshAt(_clock())) {
      return _expireSession();
    }
    final result = await _oidcClient.refresh(refreshToken);
    if (epoch != _epoch) {
      return null;
    }
    return switch (result) {
      Ok<TokenSet>(:final value) when _hasValidIdToken(value) => _tokens = tokens.mergedWith(
        value,
      ),
      Ok<TokenSet>() || Err<TokenSet>(failure: AuthenticationFailure()) => _expireSession(),
      Err<TokenSet>() => _keepSessionAfterTransientFailure(tokens),
    };
  }

  TokenSet _keepSessionAfterTransientFailure(TokenSet tokens) {
    if (tokens.isExpiredAt(_clock())) {
      throw const AccessTokenUnavailableException();
    }
    return tokens;
  }

  TokenSet _startSession(TokenSet tokens) {
    _epoch++;
    return _tokens = tokens;
  }

  TokenSet? _expireSession() {
    _endSession();
    _expired.add(null);
    return null;
  }

  void _endSession() {
    _epoch++;
    _tokens = null;
    _pendingRefresh = null;
  }

  void _forgetLoginAttempt() {
    AuthStorageKeys.all.forEach(_store.remove);
  }

  AuthUser _userOf(TokenSet tokens) => JwtClaims.userFrom(tokens.idToken ?? tokens.accessToken);
}
