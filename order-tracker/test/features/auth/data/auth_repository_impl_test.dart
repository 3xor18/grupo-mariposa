import 'dart:async';
import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:mocktail/mocktail.dart';
import 'package:order_tracker/core/error/app_failure.dart';
import 'package:order_tracker/core/http/access_token_provider.dart';
import 'package:order_tracker/core/result/result.dart';
import 'package:order_tracker/features/auth/data/auth_repository_impl.dart';
import 'package:order_tracker/features/auth/data/auth_storage_keys.dart';
import 'package:order_tracker/features/auth/data/oidc_client.dart';
import 'package:order_tracker/features/auth/data/oidc_endpoints.dart';
import 'package:order_tracker/features/auth/data/pkce.dart';
import 'package:order_tracker/features/auth/domain/auth_user.dart';
import 'package:order_tracker/features/auth/domain/session_restoration.dart';

import '../../../helpers/mocks.dart';
import '../../../helpers/tokens.dart';

void main() {
  late MockHttpClient httpClient;
  late InMemoryKeyValueStore store;
  late FakeBrowserLocation location;
  late DateTime now;
  late AuthRepositoryImpl repository;
  final redirect = Uri.parse('http://localhost:8090/');
  final endpoints = OidcEndpoints(issuer: testIssuer, clientId: testClientId);
  const analyst = AuthUser(name: 'Ana Analista', username: 'analyst');
  const signedIn = Ok<SessionRestoration>(SignedIn(analyst));

  setUpAll(registerCommonFallbacks);

  setUp(() {
    httpClient = MockHttpClient();
    store = InMemoryKeyValueStore();
    location = FakeBrowserLocation(redirect);
    now = DateTime.utc(2026, 9, 22, 12);
    DateTime clock() => now;
    repository = AuthRepositoryImpl(
      oidcClient: OidcClient(httpClient, endpoints, clock: clock),
      endpoints: endpoints,
      redirectUri: redirect,
      store: store,
      location: location,
      clock: clock,
    );
  });

  tearDown(() => repository.dispose());

  void respondTokens(Map<String, Object?> body, {int status = 200}) {
    when(
      () => httpClient.post(any(), body: any(named: 'body')),
    ).thenAnswer((_) async => http.Response(jsonEncode(body), status));
  }

  Completer<http.Response> holdTokenResponse() {
    final response = Completer<http.Response>();
    when(
      () => httpClient.post(any(), body: any(named: 'body')),
    ).thenAnswer((_) => response.future);
    return response;
  }

  void failTokensWithNetworkError() {
    when(
      () => httpClient.post(any(), body: any(named: 'body')),
    ).thenThrow(http.ClientException('offline'));
  }

  String idToken({String nonce = 'nonce-1'}) => idTokenFor(now: now, nonce: nonce);

  void prepareCallback({String state = 'state-1'}) {
    store
      ..write(AuthStorageKeys.verifier, 'verifier-1')
      ..write(AuthStorageKeys.state, 'state-1')
      ..write(AuthStorageKeys.nonce, 'nonce-1')
      ..write(AuthStorageKeys.mode, AuthRedirectModes.interactive);
    location.current = redirect.replace(queryParameters: {'state': state, 'code': 'code-1'});
  }

  Future<void> signIn() async {
    prepareCallback();
    respondTokens(tokenResponse(idToken: idToken()));
    expect(await repository.restoreSession(), signedIn);
  }

  void closeToExpiry() => now = now.add(const Duration(minutes: 4, seconds: 45));

  group('redirects', () {
    test('should store pkce, state and nonce and start an interactive login', () async {
      await repository.login();
      final uri = location.assigned.single;
      expect(uri.path, '/realms/mariposa/protocol/openid-connect/auth');
      expect(uri.queryParameters['redirect_uri'], '$redirect');
      expect(
        uri.queryParameters['code_challenge'],
        PkceGenerator.challengeFor(store.read(AuthStorageKeys.verifier) ?? ''),
      );
      expect(uri.queryParameters['state'], store.read(AuthStorageKeys.state));
      expect(uri.queryParameters['nonce'], store.read(AuthStorageKeys.nonce));
      expect(uri.queryParameters.containsKey('prompt'), isFalse);
      expect(store.read(AuthStorageKeys.mode), AuthRedirectModes.interactive);
    });

    test('should try a silent sign in when the page loads without a session', () async {
      expect(await repository.restoreSession(), const Ok<SessionRestoration>(SigningInSilently()));
      expect(location.assigned.single.queryParameters['prompt'], 'none');
      expect(store.read(AuthStorageKeys.mode), AuthRedirectModes.silent);
    });
  });

  group('callback', () {
    test('should exchange the code and keep tokens only in memory', () async {
      await signIn();
      expect(location.current, redirect);
      expect(store.values, isEmpty);
      expect(await repository.validAccessToken(), 'access-1');
      final form =
          verify(
                () => httpClient.post(any(), body: captureAny(named: 'body')),
              ).captured.single
              as Map<String, String>;
      expect(form['redirect_uri'], '$redirect');
    });

    test('should report the signed in user when restoring again', () async {
      await signIn();
      expect(await repository.restoreSession(), signedIn);
      expect(location.assigned, isEmpty);
    });

    test('should reject a callback with a forged state', () async {
      prepareCallback(state: 'forged');
      expect(
        await repository.restoreSession(),
        const Err<SessionRestoration>(
          AuthenticationFailure(AuthenticationFailureReason.stateMismatch),
        ),
      );
      verifyNever(() => httpClient.post(any(), body: any(named: 'body')));
    });

    test('should reject a callback without a stored nonce', () async {
      prepareCallback();
      store.remove(AuthStorageKeys.nonce);
      expect(await repository.restoreSession(), isA<Err<SessionRestoration>>());
    });

    test('should reject an id token issued for another nonce', () async {
      prepareCallback();
      respondTokens(tokenResponse(idToken: idToken(nonce: 'replayed')));
      expect(
        await repository.restoreSession(),
        const Err<SessionRestoration>(
          AuthenticationFailure(AuthenticationFailureReason.invalidIdToken),
        ),
      );
      expect(await repository.validAccessToken(), isNull);
    });

    test('should reject a code exchange without id token', () async {
      prepareCallback();
      respondTokens(tokenResponse());
      expect(await repository.restoreSession(), isA<Err<SessionRestoration>>());
    });

    test('should surface token exchange failures', () async {
      prepareCallback();
      respondTokens({'error': 'invalid_grant'}, status: 400);
      expect(
        await repository.restoreSession(),
        const Err<SessionRestoration>(
          AuthenticationFailure(AuthenticationFailureReason.tokenExchange),
        ),
      );
    });

    test('should treat a failed silent sign in as signed out', () async {
      store.write(AuthStorageKeys.mode, AuthRedirectModes.silent);
      location.current = redirect.replace(queryParameters: {'error': 'login_required'});
      expect(await repository.restoreSession(), const Ok<SessionRestoration>(SignedOut()));
      expect(location.current, redirect);
      expect(store.values, isEmpty);
    });

    test('should report an interactive login rejected by keycloak', () async {
      store.write(AuthStorageKeys.mode, AuthRedirectModes.interactive);
      location.current = redirect.replace(queryParameters: {'error': 'access_denied'});
      expect(
        await repository.restoreSession(),
        const Err<SessionRestoration>(
          AuthenticationFailure(AuthenticationFailureReason.callbackRejected),
        ),
      );
    });
  });

  group('validAccessToken', () {
    test('should return nothing without a session', () async {
      expect(await repository.validAccessToken(), isNull);
    });

    test('should refresh once for concurrent requests close to expiry', () async {
      await signIn();
      final response = holdTokenResponse();
      closeToExpiry();
      final first = repository.validAccessToken();
      final second = repository.validAccessToken();
      response.complete(http.Response(jsonEncode(tokenResponse(accessToken: 'access-2')), 200));
      expect(await Future.wait([first, second]), ['access-2', 'access-2']);
      expect(await repository.validAccessToken(), 'access-2');
    });

    test('should end the session when keycloak rejects the refresh token', () async {
      await signIn();
      respondTokens({'error': 'invalid_grant'}, status: 400);
      now = now.add(const Duration(minutes: 10));
      final expirations = expectLater(repository.sessionExpired, emits(null));
      expect(await repository.validAccessToken(), isNull);
      await expirations;
    });

    test('should end the session when the refreshed id token is invalid', () async {
      await signIn();
      final foreign = idTokenFor(now: now, audience: 'someone-else');
      respondTokens(tokenResponse(accessToken: 'access-2', idToken: foreign));
      closeToExpiry();
      expect(await repository.validAccessToken(), isNull);
    });

    test('should end the session when there is no refresh token', () async {
      prepareCallback();
      respondTokens(tokenResponse(idToken: idToken(), refreshToken: null));
      await repository.restoreSession();
      now = now.add(const Duration(minutes: 10));
      expect(await repository.validAccessToken(), isNull);
    });

    test('should keep the session and the current token on a network failure', () async {
      await signIn();
      failTokensWithNetworkError();
      closeToExpiry();
      var expired = false;
      final subscription = repository.sessionExpired.listen((_) => expired = true);
      expect(await repository.validAccessToken(), 'access-1');
      await pumpEventQueue();
      expect(expired, isFalse);
      await subscription.cancel();
    });

    test('should report the token as unavailable when it already expired offline', () async {
      await signIn();
      failTokensWithNetworkError();
      now = now.add(const Duration(minutes: 6));
      await expectLater(
        repository.validAccessToken(),
        throwsA(isA<AccessTokenUnavailableException>()),
      );
      respondTokens(tokenResponse(accessToken: 'access-2'));
      expect(await repository.validAccessToken(), 'access-2');
    });

    test('should keep the session when keycloak answers with a server error', () async {
      await signIn();
      respondTokens({'error': 'unavailable'}, status: 503);
      closeToExpiry();
      expect(await repository.validAccessToken(), 'access-1');
    });

    test('should ignore a refresh that completes after logout', () async {
      await signIn();
      final response = holdTokenResponse();
      closeToExpiry();
      final pending = repository.validAccessToken();
      await repository.logout();
      response.complete(http.Response(jsonEncode(tokenResponse(accessToken: 'late')), 200));
      expect(await pending, isNull);
      expect(await repository.validAccessToken(), isNull);
    });

    test('should ignore a refresh that completes after the session was rejected', () async {
      await signIn();
      final response = holdTokenResponse();
      closeToExpiry();
      final pending = repository.validAccessToken();
      repository.onUnauthorized('access-1');
      response.complete(http.Response(jsonEncode(tokenResponse(accessToken: 'late')), 200));
      expect(await pending, isNull);
      expect(await repository.validAccessToken(), isNull);
    });
  });

  group('onUnauthorized', () {
    test('should end the session when the current token is rejected', () async {
      await signIn();
      final expirations = expectLater(repository.sessionExpired, emits(null));
      repository.onUnauthorized('access-1');
      await expirations;
      expect(await repository.validAccessToken(), isNull);
    });

    test('should ignore rejections of tokens that were already replaced', () async {
      await signIn();
      repository
        ..onUnauthorized('previous-token')
        ..onUnauthorized(null);
      expect(await repository.validAccessToken(), 'access-1');
    });
  });

  test('should clear the session and redirect to the end session endpoint', () async {
    await signIn();
    await repository.logout();
    final uri = location.assigned.single;
    expect(uri.path, '/realms/mariposa/protocol/openid-connect/logout');
    expect(uri.queryParameters['id_token_hint'], isNotNull);
    expect(uri.queryParameters['post_logout_redirect_uri'], '$redirect');
    expect(await repository.validAccessToken(), isNull);
  });
}
