import 'dart:convert';

String jwt(Map<String, Object?> claims) {
  String encode(Object value) =>
      base64Url.encode(utf8.encode(jsonEncode(value))).replaceAll('=', '');
  return '${encode({'alg': 'RS256'})}.${encode(claims)}.signature';
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
