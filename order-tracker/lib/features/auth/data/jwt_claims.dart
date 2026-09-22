import 'dart:convert';

import 'package:order_tracker/core/json/json_map.dart';
import 'package:order_tracker/features/auth/domain/auth_user.dart';

abstract final class JwtClaims {
  static const _separator = '.';
  static const _payloadIndex = 1;
  static const _segmentCount = 3;
  static const _name = 'name';
  static const _preferredUsername = 'preferred_username';
  static const _email = 'email';

  static AuthUser userFrom(String token) {
    final claims = decodePayload(token);
    if (claims == null) {
      return const AuthUser();
    }
    return AuthUser(
      name: claims.optionalString(_name),
      username: claims.optionalString(_preferredUsername),
      email: claims.optionalString(_email),
    );
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
