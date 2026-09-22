import 'package:order_tracker/core/json/json_map.dart';
import 'package:order_tracker/features/auth/data/jwt_claims.dart';

final class IdTokenValidator {
  const IdTokenValidator({required this.issuer, required this.clientId});

  static const _millisecondsPerSecond = 1000;

  final Uri issuer;
  final String clientId;

  bool isValid(String idToken, {required DateTime now, String? expectedNonce}) {
    final claims = JwtClaims.decodePayload(idToken);
    if (claims == null) {
      return false;
    }
    try {
      return _hasIssuer(claims) &&
          _hasAudience(claims) &&
          _isUnexpired(claims, now) &&
          _hasNonce(claims, expectedNonce);
    } on FormatException {
      return false;
    }
  }

  bool _hasIssuer(JsonMap claims) => claims.optionalString(JwtClaimNames.issuer) == '$issuer';

  bool _hasAudience(JsonMap claims) {
    return switch (claims.raw[JwtClaimNames.audience]) {
      final String audience => audience == clientId,
      final List<Object?> audiences => audiences.contains(clientId),
      Object() || null => false,
    };
  }

  bool _isUnexpired(JsonMap claims, DateTime now) {
    final seconds = claims.requireInt(JwtClaimNames.expiration);
    final expiresAt = DateTime.fromMillisecondsSinceEpoch(
      seconds * _millisecondsPerSecond,
      isUtc: true,
    );
    return now.isBefore(expiresAt);
  }

  bool _hasNonce(JsonMap claims, String? expectedNonce) {
    return expectedNonce == null || claims.optionalString(JwtClaimNames.nonce) == expectedNonce;
  }
}
