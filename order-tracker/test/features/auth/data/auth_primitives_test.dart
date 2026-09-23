import 'dart:convert';
import 'dart:math';

import 'package:flutter_test/flutter_test.dart';
import 'package:order_tracker/core/config/app_config.dart';
import 'package:order_tracker/core/json/json_map.dart';
import 'package:order_tracker/features/auth/data/authorization_callback.dart';
import 'package:order_tracker/features/auth/data/id_token_validator.dart';
import 'package:order_tracker/features/auth/data/jwt_claims.dart';
import 'package:order_tracker/features/auth/data/oidc_endpoints.dart';
import 'package:order_tracker/features/auth/data/pkce.dart';
import 'package:order_tracker/features/auth/data/token_set.dart';
import 'package:order_tracker/features/auth/domain/auth_user.dart';

import '../../../helpers/tokens.dart';

void main() {
  group('PkceGenerator', () {
    test('should derive the S256 challenge from the RFC 7636 example verifier', () {
      expect(
        PkceGenerator.challengeFor('dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk'),
        'E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM',
      );
    });

    test('should generate url safe verifiers without padding', () {
      final pair = PkceGenerator(random: Random(7)).generate();
      expect(pair.verifier, hasLength(43));
      expect(pair.verifier, matches(RegExp(r'^[A-Za-z0-9_-]+$')));
      expect(pair.challenge, PkceGenerator.challengeFor(pair.verifier));
      expect(pair.challenge, isNot(contains('=')));
    });

    test('should generate different values on each call', () {
      final generator = PkceGenerator();
      expect(generator.generate(), isNot(generator.generate()));
      expect(generator.randomToken(PkceGenerator.stateByteLength), hasLength(22));
    });
  });

  group('OidcEndpoints', () {
    final endpoints = OidcEndpoints.fromConfig(
      const AppConfig(
        apiBaseUrl: '/api',
        keycloakUrl: 'http://localhost:8180',
        realm: 'mariposa',
        clientId: 'order-tracker',
        redirectUri: 'http://localhost:8090/',
      ),
    );
    final redirect = Uri.parse('http://localhost:8090/');

    test('should build the token endpoint', () {
      expect(
        endpoints.token.toString(),
        'http://localhost:8180/realms/mariposa/protocol/openid-connect/token',
      );
    });

    test('should build the authorization request with pkce parameters', () {
      final uri = endpoints.authorization(
        redirectUri: redirect,
        state: 'state-1',
        codeChallenge: 'challenge-1',
        nonce: 'nonce-1',
      );
      expect(uri.path, '/realms/mariposa/protocol/openid-connect/auth');
      expect(uri.queryParameters, {
        'response_type': 'code',
        'client_id': 'order-tracker',
        'redirect_uri': 'http://localhost:8090/',
        'scope': 'openid profile email',
        'state': 'state-1',
        'code_challenge': 'challenge-1',
        'code_challenge_method': 'S256',
        'nonce': 'nonce-1',
      });
    });

    test('should ask keycloak not to prompt during silent sign in', () {
      final uri = endpoints.authorization(
        redirectUri: redirect,
        state: 's',
        codeChallenge: 'c',
        nonce: 'n',
        prompt: OidcValues.silentPrompt,
      );
      expect(uri.queryParameters['prompt'], 'none');
    });

    test('should build the logout request with and without id token hint', () {
      final withHint = endpoints.logout(redirectUri: redirect, idTokenHint: 'id-1');
      expect(withHint.path, '/realms/mariposa/protocol/openid-connect/logout');
      expect(withHint.queryParameters['id_token_hint'], 'id-1');
      expect(withHint.queryParameters['post_logout_redirect_uri'], 'http://localhost:8090/');
      final withoutHint = endpoints.logout(redirectUri: redirect);
      expect(withoutHint.queryParameters.containsKey('id_token_hint'), isFalse);
    });
  });

  group('AuthorizationCallback', () {
    test('should detect the absence of a callback', () {
      expect(
        AuthorizationCallback.parse(Uri.parse('http://localhost:8090/#/')),
        const NoAuthorizationCallback(),
      );
    });

    test('should parse the authorization code and state', () {
      expect(
        AuthorizationCallback.parse(
          Uri.parse('http://localhost:8090/?state=s1&session_state=x&code=c1'),
        ),
        const AuthorizationGranted(code: 'c1', state: 's1'),
      );
    });

    test('should parse authorization errors', () {
      expect(
        AuthorizationCallback.parse(Uri.parse('http://localhost:8090/?error=access_denied')),
        const AuthorizationDenied('access_denied'),
      );
    });
  });

  group('TokenSet', () {
    final issuedAt = DateTime.utc(2026, 9, 22, 12);

    test('should compute expirations from the token response', () {
      final tokens = TokenSet.fromTokenResponse(
        const JsonMap({
          'access_token': 'a',
          'refresh_token': 'r',
          'id_token': 'i',
          'expires_in': 300,
          'refresh_expires_in': 1800,
          'token_type': 'Bearer',
        }),
        issuedAt: issuedAt,
      );
      expect(tokens.expiresAt, issuedAt.add(const Duration(minutes: 5)));
      expect(tokens.refreshExpiresAt, issuedAt.add(const Duration(minutes: 30)));
    });

    test('should treat a zero refresh expiration as non expiring', () {
      final tokens = TokenSet.fromTokenResponse(
        const JsonMap({'access_token': 'a', 'expires_in': 60, 'refresh_expires_in': 0}),
        issuedAt: issuedAt,
      );
      expect(tokens.refreshExpiresAt, isNull);
      expect(tokens.refreshToken, isNull);
    });

    test('should know when it expires within a margin', () {
      final tokens = TokenSet(accessToken: 'a', expiresAt: issuedAt);
      const margin = Duration(seconds: 30);
      expect(tokens.expiresWithin(issuedAt.subtract(const Duration(minutes: 1)), margin), false);
      expect(tokens.expiresWithin(issuedAt.subtract(const Duration(seconds: 10)), margin), true);
      expect(tokens.expiresWithin(issuedAt, margin), isTrue);
    });

    test('should know whether it can still be refreshed', () {
      final limited = TokenSet(
        accessToken: 'a',
        refreshToken: 'r',
        expiresAt: issuedAt,
        refreshExpiresAt: issuedAt.add(const Duration(minutes: 30)),
      );
      expect(limited.canRefreshAt(issuedAt), isTrue);
      expect(limited.canRefreshAt(issuedAt.add(const Duration(hours: 1))), isFalse);
      expect(TokenSet(accessToken: 'a', expiresAt: issuedAt).canRefreshAt(issuedAt), isFalse);
      expect(
        TokenSet(accessToken: 'a', refreshToken: 'r', expiresAt: issuedAt).canRefreshAt(issuedAt),
        isTrue,
      );
    });

    test('should keep previous tokens missing from a refresh response', () {
      final original = TokenSet(
        accessToken: 'a1',
        refreshToken: 'r1',
        idToken: 'i1',
        expiresAt: issuedAt,
        refreshExpiresAt: issuedAt,
      );
      final merged = original.mergedWith(TokenSet(accessToken: 'a2', expiresAt: issuedAt));
      expect(merged.accessToken, 'a2');
      expect(merged.refreshToken, 'r1');
      expect(merged.idToken, 'i1');
      expect(merged.refreshExpiresAt, issuedAt);
    });

    test('should know when it already expired', () {
      final tokens = TokenSet(accessToken: 'a', expiresAt: issuedAt);
      expect(tokens.isExpiredAt(issuedAt.subtract(const Duration(seconds: 1))), isFalse);
      expect(tokens.isExpiredAt(issuedAt), isTrue);
    });
  });

  group('IdTokenValidator', () {
    final now = DateTime.utc(2026, 9, 22, 12);
    final validator = IdTokenValidator(issuer: testIssuer, clientId: testClientId);

    bool valid(String token, {String? nonce}) {
      return validator.isValid(token, now: now, expectedNonce: nonce);
    }

    test('should accept a token for this client, issuer and nonce', () {
      expect(
        valid(
          idTokenFor(now: now, nonce: 'n1'),
          nonce: 'n1',
        ),
        isTrue,
      );
      expect(valid(idTokenFor(now: now, audience: ['other', testClientId])), isTrue);
      expect(valid(idTokenFor(now: now, nonce: 'n1')), isTrue);
    });

    test('should reject tokens with the wrong nonce, audience, issuer or expiry', () {
      expect(
        valid(
          idTokenFor(now: now, nonce: 'n2'),
          nonce: 'n1',
        ),
        isFalse,
      );
      expect(valid(idTokenFor(now: now), nonce: 'n1'), isFalse);
      expect(valid(idTokenFor(now: now, audience: 'other')), isFalse);
      expect(valid(idTokenFor(now: now, audience: 42)), isFalse);
      expect(valid(idTokenFor(now: now, issuer: 'https://evil.example')), isFalse);
      expect(valid(idTokenFor(now: now, validFor: Duration.zero)), isFalse);
    });

    test('should reject malformed tokens', () {
      expect(valid('not-a-jwt'), isFalse);
      expect(valid(jwt({'iss': '$testIssuer', 'aud': testClientId, 'exp': 'soon'})), isFalse);
    });
  });

  group('JwtClaims', () {
    test('should read the user from the token payload', () {
      final token = jwt({
        'name': 'Ana Analista',
        'preferred_username': 'analyst',
        'email': 'analyst@grupomariposa.dev',
      });
      expect(
        JwtClaims.userFrom(token),
        const AuthUser(
          name: 'Ana Analista',
          username: 'analyst',
          email: 'analyst@grupomariposa.dev',
        ),
      );
    });

    test('should fall back to an anonymous user for malformed tokens', () {
      expect(JwtClaims.userFrom('not-a-jwt'), const AuthUser());
      expect(JwtClaims.userFrom('a.%%%.c'), const AuthUser());
      expect(JwtClaims.decodePayload('a.${base64Url.encode(utf8.encode('[1]'))}.c'), isNull);
    });

    test('should choose the best display name available', () {
      expect(const AuthUser(name: 'Ana', username: 'analyst').displayName, 'Ana');
      expect(const AuthUser(username: 'analyst', email: 'a@b.c').displayName, 'analyst');
      expect(const AuthUser(email: 'a@b.c').displayName, 'a@b.c');
      expect(const AuthUser().displayName, isNull);
    });
  });
}
