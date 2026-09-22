import 'dart:async';
import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:mocktail/mocktail.dart';
import 'package:order_tracker/core/error/app_failure.dart';
import 'package:order_tracker/core/result/result.dart';
import 'package:order_tracker/features/auth/data/auth_repository_impl.dart';
import 'package:order_tracker/features/auth/data/oidc_client.dart';
import 'package:order_tracker/features/auth/data/oidc_endpoints.dart';
import 'package:order_tracker/features/auth/data/pkce.dart';
import 'package:order_tracker/features/auth/data/token_set.dart';
import 'package:order_tracker/features/auth/domain/auth_user.dart';

import '../../../helpers/mocks.dart';
import '../../../helpers/tokens.dart';

void main() {
  late MockHttpClient httpClient;
  late InMemoryKeyValueStore store;
  late FakeBrowserLocation location;
  late DateTime now;
  late AuthRepositoryImpl repository;
  final appUri = Uri.parse('http://localhost:8090/');
  final endpoints = OidcEndpoints(
    issuer: Uri.parse('http://localhost:8180/realms/mariposa'),
    clientId: 'order-tracker',
  );
  final idToken = jwt({'name': 'Ana Analista', 'preferred_username': 'analyst'});
  const analyst = AuthUser(name: 'Ana Analista', username: 'analyst');

  setUpAll(registerCommonFallbacks);

  AuthRepositoryImpl buildRepository() {
    DateTime clock() => now;
    return AuthRepositoryImpl(
      oidcClient: OidcClient(httpClient, endpoints, clock: clock),
      endpoints: endpoints,
      store: store,
      location: location,
      clock: clock,
    );
  }

  setUp(() {
    httpClient = MockHttpClient();
    store = InMemoryKeyValueStore();
    location = FakeBrowserLocation(appUri);
    now = DateTime.utc(2026, 9, 22, 12);
    repository = buildRepository();
  });

  tearDown(() => repository.dispose());

  void respondTokens(Map<String, Object?> body, {int status = 200}) {
    when(
      () => httpClient.post(any(), body: any(named: 'body')),
    ).thenAnswer((_) async => http.Response(jsonEncode(body), status));
  }

  void storeSession(TokenSet tokens) {
    store.write(AuthStorageKeys.tokens, jsonEncode(tokens.toStorage()));
  }

  TokenSet session({
    Duration expiresIn = const Duration(minutes: 5),
    String? refreshToken = 'refresh-1',
  }) {
    return TokenSet(
      accessToken: 'access-1',
      refreshToken: refreshToken,
      idToken: idToken,
      expiresAt: now.add(expiresIn),
    );
  }

  group('login', () {
    test('should store a pkce verifier and redirect to the authorization endpoint', () async {
      await repository.login();
      final verifier = store.read(AuthStorageKeys.verifier)!;
      final state = store.read(AuthStorageKeys.state)!;
      final redirect = location.assigned.single;
      expect(redirect.path, '/realms/mariposa/protocol/openid-connect/auth');
      expect(redirect.queryParameters['code_challenge'], PkceGenerator.challengeFor(verifier));
      expect(redirect.queryParameters['state'], state);
      expect(redirect.queryParameters['redirect_uri'], 'http://localhost:8090/');
    });
  });

  group('restoreSession with a callback', () {
    setUp(() {
      store
        ..write(AuthStorageKeys.verifier, 'verifier-1')
        ..write(AuthStorageKeys.state, 'state-1');
    });

    test('should exchange the code, clean the address and persist the session', () async {
      location.current = Uri.parse('http://localhost:8090/?state=state-1&code=code-1');
      respondTokens(tokenResponse(idToken: idToken));
      final result = await repository.restoreSession();
      expect(result, const Ok<AuthUser?>(analyst));
      expect(location.current, appUri);
      expect(store.read(AuthStorageKeys.verifier), isNull);
      expect(store.read(AuthStorageKeys.tokens), isNotNull);
      expect(await repository.validAccessToken(), 'access-1');
    });

    test('should reject a callback whose state does not match', () async {
      location.current = Uri.parse('http://localhost:8090/?state=forged&code=code-1');
      final result = await repository.restoreSession();
      expect(
        result,
        const Err<AuthUser?>(AuthenticationFailure(AuthenticationFailureReason.stateMismatch)),
      );
      verifyNever(() => httpClient.post(any(), body: any(named: 'body')));
    });

    test('should reject a callback without a stored verifier', () async {
      store.remove(AuthStorageKeys.verifier);
      location.current = Uri.parse('http://localhost:8090/?state=state-1&code=code-1');
      expect(await repository.restoreSession(), isA<Err<AuthUser?>>());
    });

    test('should surface token exchange failures', () async {
      location.current = Uri.parse('http://localhost:8090/?state=state-1&code=code-1');
      respondTokens({'error': 'invalid_grant'}, status: 400);
      expect(
        await repository.restoreSession(),
        const Err<AuthUser?>(AuthenticationFailure(AuthenticationFailureReason.tokenExchange)),
      );
      expect(store.read(AuthStorageKeys.tokens), isNull);
    });

    test('should report callbacks rejected by keycloak', () async {
      location.current = Uri.parse('http://localhost:8090/?error=access_denied');
      expect(
        await repository.restoreSession(),
        const Err<AuthUser?>(AuthenticationFailure(AuthenticationFailureReason.callbackRejected)),
      );
      expect(location.current, appUri);
      expect(store.read(AuthStorageKeys.state), isNull);
    });
  });

  group('restoreSession from storage', () {
    test('should return no user when there is no stored session', () async {
      expect(await repository.restoreSession(), const Ok<AuthUser?>(null));
      expect(await repository.validAccessToken(), isNull);
    });

    test('should restore a valid stored session without refreshing', () async {
      storeSession(session());
      expect(await repository.restoreSession(), const Ok<AuthUser?>(analyst));
      verifyNever(() => httpClient.post(any(), body: any(named: 'body')));
    });

    test('should refresh a stored session that is about to expire', () async {
      storeSession(session(expiresIn: const Duration(seconds: 10)));
      respondTokens(tokenResponse(accessToken: 'access-2'));
      expect(await repository.restoreSession(), const Ok<AuthUser?>(analyst));
      expect(await repository.validAccessToken(), 'access-2');
    });

    test('should drop a stored session that cannot be refreshed', () async {
      storeSession(session(expiresIn: Duration.zero, refreshToken: null));
      expect(await repository.restoreSession(), const Ok<AuthUser?>(null));
      expect(store.read(AuthStorageKeys.tokens), isNull);
    });

    test('should drop a stored session when the refresh is rejected', () async {
      storeSession(session(expiresIn: Duration.zero));
      respondTokens({'error': 'invalid_grant'}, status: 400);
      expect(await repository.restoreSession(), const Ok<AuthUser?>(null));
      expect(store.read(AuthStorageKeys.tokens), isNull);
    });

    test('should discard a corrupted stored session', () async {
      store.write(AuthStorageKeys.tokens, '{"accessToken": 1}');
      expect(await repository.restoreSession(), const Ok<AuthUser?>(null));
      expect(store.read(AuthStorageKeys.tokens), isNull);
    });
  });

  group('validAccessToken', () {
    setUp(() async {
      storeSession(session());
      await repository.restoreSession();
    });

    test('should refresh once for concurrent requests before the token expires', () async {
      final response = Completer<http.Response>();
      when(
        () => httpClient.post(any(), body: any(named: 'body')),
      ).thenAnswer((_) => response.future);
      now = now.add(const Duration(minutes: 4, seconds: 45));
      final first = repository.validAccessToken();
      final second = repository.validAccessToken();
      response.complete(http.Response(jsonEncode(tokenResponse(accessToken: 'access-2')), 200));
      expect(await Future.wait([first, second]), ['access-2', 'access-2']);
      verify(() => httpClient.post(any(), body: any(named: 'body'))).called(1);
    });

    test('should announce an expired session when the refresh fails', () async {
      respondTokens({'error': 'invalid_grant'}, status: 400);
      now = now.add(const Duration(minutes: 10));
      final expirations = expectLater(repository.sessionExpired, emits(null));
      expect(await repository.validAccessToken(), isNull);
      await expirations;
    });
  });

  group('onUnauthorized', () {
    test('should clear the session and announce the expiration', () async {
      storeSession(session());
      await repository.restoreSession();
      final expirations = expectLater(repository.sessionExpired, emits(null));
      repository.onUnauthorized();
      await expirations;
      expect(await repository.validAccessToken(), isNull);
    });

    test('should ignore unauthorized responses without a session', () async {
      var announced = false;
      final subscription = repository.sessionExpired.listen((_) => announced = true);
      repository.onUnauthorized();
      await pumpEventQueue();
      expect(announced, isFalse);
      await subscription.cancel();
    });
  });

  group('logout', () {
    test('should clear the session and redirect to the end session endpoint', () async {
      storeSession(session());
      await repository.restoreSession();
      await repository.logout();
      final redirect = location.assigned.single;
      expect(redirect.path, '/realms/mariposa/protocol/openid-connect/logout');
      expect(redirect.queryParameters['id_token_hint'], idToken);
      expect(store.read(AuthStorageKeys.tokens), isNull);
    });
  });
}
