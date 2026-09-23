import 'dart:convert';

final Uri testIssuer = Uri.parse('http://localhost:8180/realms/mariposa');
const testClientId = 'order-tracker';
const _millisecondsPerSecond = 1000;

String jwt(Map<String, Object?> claims) {
  String encode(Object value) =>
      base64Url.encode(utf8.encode(jsonEncode(value))).replaceAll('=', '');
  return '${encode({'alg': 'RS256'})}.${encode(claims)}.signature';
}

String idTokenFor({
  required DateTime now,
  String? nonce,
  Object audience = testClientId,
  String? issuer,
  Duration validFor = const Duration(minutes: 5),
}) {
  return jwt({
    'iss': issuer ?? '$testIssuer',
    'aud': audience,
    'exp': now.add(validFor).millisecondsSinceEpoch ~/ _millisecondsPerSecond,
    'nonce': ?nonce,
    'name': 'Ana Analista',
    'preferred_username': 'analyst',
  });
}

Map<String, Object?> tokenResponse({
  String accessToken = 'access-1',
  String? refreshToken = 'refresh-1',
  String? idToken,
  int expiresIn = 300,
  int refreshExpiresIn = 1800,
}) {
  return {
    'access_token': accessToken,
    'refresh_token': ?refreshToken,
    'id_token': ?idToken,
    'expires_in': expiresIn,
    'refresh_expires_in': refreshExpiresIn,
    'token_type': 'Bearer',
  };
}
