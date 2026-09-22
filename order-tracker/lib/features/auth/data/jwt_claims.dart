import 'dart:convert';

import 'package:order_tracker/core/json/json_map.dart';
import 'package:order_tracker/features/auth/domain/auth_user.dart';

abstract final class JwtClaimNames {
  static const name = 'name';
  static const preferredUsername = 'preferred_username';
  static const email = 'email';
  static const issuer = 'iss';
  static const audience = 'aud';
  static const expiration = 'exp';
  static const nonce = 'nonce';
}

abstract final class JwtClaims {
  static const _separator = '.';
  static const _payloadIndex = 1;
  static const _segmentCount = 3;

  static AuthUser userFrom(String token) {
    return switch (decodePayload(token)) {
      final JsonMap claims => AuthUser(
        name: claims.optionalString(JwtClaimNames.name),
        username: claims.optionalString(JwtClaimNames.preferredUsername),
        email: claims.optionalString(JwtClaimNames.email),
      ),
      null => const AuthUser(),
    };
  }

  static JsonMap? decodePayload(String token) {
    final segments = token.split(_separator);
    if (segments.length != _segmentCount) {
      return null;
    }
    try {
      final payload = utf8.decode(base64Url.decode(base64Url.normalize(segments[_payloadIndex])));
      return JsonMap.parse(jsonDecode(payload));
    } on FormatException {
      return null;
    }
  }
}
