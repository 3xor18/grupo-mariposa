import 'package:equatable/equatable.dart';
import 'package:order_tracker/core/json/json_map.dart';

abstract final class _TokenResponseFields {
  static const accessToken = 'access_token';
  static const refreshToken = 'refresh_token';
  static const idToken = 'id_token';
  static const expiresIn = 'expires_in';
  static const refreshExpiresIn = 'refresh_expires_in';
}

abstract final class _StoredFields {
  static const accessToken = 'accessToken';
  static const refreshToken = 'refreshToken';
  static const idToken = 'idToken';
  static const expiresAt = 'expiresAt';
  static const refreshExpiresAt = 'refreshExpiresAt';
}

final class TokenSet extends Equatable {
  const TokenSet({
    required this.accessToken,
    required this.expiresAt,
    this.refreshToken,
    this.idToken,
    this.refreshExpiresAt,
  });

  factory TokenSet.fromTokenResponse(JsonMap json, {required DateTime issuedAt}) {
    final refreshExpiresIn = json.optionalInt(_TokenResponseFields.refreshExpiresIn);
    return TokenSet(
      accessToken: json.requireString(_TokenResponseFields.accessToken),
      refreshToken: json.optionalString(_TokenResponseFields.refreshToken),
      idToken: json.optionalString(_TokenResponseFields.idToken),
      expiresAt: issuedAt.add(Duration(seconds: json.requireInt(_TokenResponseFields.expiresIn))),
      refreshExpiresAt: _refreshExpiry(issuedAt, refreshExpiresIn),
    );
  }

  factory TokenSet.fromStorage(JsonMap json) {
    return TokenSet(
      accessToken: json.requireString(_StoredFields.accessToken),
      refreshToken: json.optionalString(_StoredFields.refreshToken),
      idToken: json.optionalString(_StoredFields.idToken),
      expiresAt: json.requireDateTime(_StoredFields.expiresAt),
      refreshExpiresAt: json.optionalDateTime(_StoredFields.refreshExpiresAt),
    );
  }

  final String accessToken;
  final String? refreshToken;
  final String? idToken;
  final DateTime expiresAt;
  final DateTime? refreshExpiresAt;

  bool expiresWithin(DateTime now, Duration margin) => !now.add(margin).isBefore(expiresAt);

  bool canRefreshAt(DateTime now) {
    final refreshExpiresAt = this.refreshExpiresAt;
    return refreshToken != null && (refreshExpiresAt == null || now.isBefore(refreshExpiresAt));
  }

  TokenSet mergedWith(TokenSet refreshed) {
    return TokenSet(
      accessToken: refreshed.accessToken,
      refreshToken: refreshed.refreshToken ?? refreshToken,
      idToken: refreshed.idToken ?? idToken,
      expiresAt: refreshed.expiresAt,
      refreshExpiresAt: refreshed.refreshExpiresAt ?? refreshExpiresAt,
    );
  }

  Map<String, Object?> toStorage() => {
    _StoredFields.accessToken: accessToken,
    _StoredFields.refreshToken: refreshToken,
    _StoredFields.idToken: idToken,
    _StoredFields.expiresAt: expiresAt.toUtc().toIso8601String(),
    _StoredFields.refreshExpiresAt: refreshExpiresAt?.toUtc().toIso8601String(),
  };

  static DateTime? _refreshExpiry(DateTime issuedAt, int? seconds) {
    return seconds == null || seconds <= 0 ? null : issuedAt.add(Duration(seconds: seconds));
  }

  @override
  List<Object?> get props => [accessToken, refreshToken, idToken, expiresAt, refreshExpiresAt];
}
