import 'dart:async';
import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:mocktail/mocktail.dart';
import 'package:order_tracker/core/error/app_failure.dart';
import 'package:order_tracker/core/result/result.dart';
import 'package:order_tracker/features/auth/data/oidc_client.dart';
import 'package:order_tracker/features/auth/data/oidc_endpoints.dart';
import 'package:order_tracker/features/auth/data/token_set.dart';

import '../../../helpers/mocks.dart';
import '../../../helpers/tokens.dart';

void main() {
  late MockHttpClient httpClient;
  late OidcClient client;
  final now = DateTime.utc(2026, 9, 22, 12);
  final endpoints = OidcEndpoints(
    issuer: Uri.parse('http://localhost:8180/realms/mariposa'),
    clientId: 'order-tracker',
  );

  setUpAll(registerCommonFallbacks);

  setUp(() {
    httpClient = MockHttpClient();
    client = OidcClient(httpClient, endpoints, clock: () => now);
  });

  void respond(http.Response response) {
    when(
      () => httpClient.post(any(), body: any(named: 'body')),
    ).thenAnswer((_) async => response);
  }

  Map<String, String> postedForm() {
    return verify(
          () => httpClient.post(endpoints.token, body: captureAny(named: 'body')),
        ).captured.single
        as Map<String, String>;
  }

  test('should exchange the authorization code with the pkce verifier', () async {
    respond(http.Response(jsonEncode(tokenResponse()), 200));
    final result = await client.exchangeCode(
      code: 'code-1',
      verifier: 'verifier-1',
      redirectUri: Uri.parse('http://localhost:8090/'),
    );
    expect(
      result,
      Ok(
        TokenSet(
          accessToken: 'access-1',
          refreshToken: 'refresh-1',
          expiresAt: now.add(const Duration(minutes: 5)),
          refreshExpiresAt: now.add(const Duration(minutes: 30)),
        ),
      ),
    );
    expect(postedForm(), {
      'grant_type': 'authorization_code',
      'client_id': 'order-tracker',
      'code': 'code-1',
      'code_verifier': 'verifier-1',
      'redirect_uri': 'http://localhost:8090/',
    });
  });

  test('should refresh tokens with the refresh token grant', () async {
    respond(http.Response(jsonEncode(tokenResponse(accessToken: 'access-2')), 200));
    final result = await client.refresh('refresh-1');
    expect(result, isA<Ok<TokenSet>>());
    expect(postedForm(), {
      'grant_type': 'refresh_token',
      'client_id': 'order-tracker',
      'refresh_token': 'refresh-1',
    });
  });

  test('should fail with an authentication failure when keycloak rejects the grant', () async {
    respond(http.Response('{"error":"invalid_grant"}', 400));
    expect(
      await client.refresh('expired'),
      const Err<TokenSet>(AuthenticationFailure(AuthenticationFailureReason.tokenExchange)),
    );
  });

  test('should fail with unexpected response when the body is malformed', () async {
    respond(http.Response('{"expires_in": 1}', 200));
    expect(await client.refresh('r'), const Err<TokenSet>(UnexpectedResponseFailure()));
  });

  test('should fail with network failure when keycloak is unreachable', () async {
    when(
      () => httpClient.post(any(), body: any(named: 'body')),
    ).thenThrow(http.ClientException('offline'));
    expect(await client.refresh('r'), const Err<TokenSet>(NetworkFailure()));
  });

  test('should fail with network failure when keycloak does not answer in time', () async {
    final slowClient = OidcClient(httpClient, endpoints, timeout: Duration.zero);
    when(
      () => httpClient.post(any(), body: any(named: 'body')),
    ).thenAnswer((_) => Completer<http.Response>().future);
    expect(await slowClient.refresh('r'), const Err<TokenSet>(NetworkFailure()));
  });
}
