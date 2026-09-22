import 'dart:async';
import 'dart:convert';

import 'package:http/http.dart' as http;
import 'package:order_tracker/core/error/app_failure.dart';
import 'package:order_tracker/core/http/app_timeouts.dart';
import 'package:order_tracker/core/http/http_status_codes.dart';
import 'package:order_tracker/core/json/json_map.dart';
import 'package:order_tracker/core/result/result.dart';
import 'package:order_tracker/core/time/clock.dart';
import 'package:order_tracker/features/auth/data/oidc_endpoints.dart';
import 'package:order_tracker/features/auth/data/token_set.dart';

final class OidcClient {
  OidcClient(
    this._httpClient,
    this._endpoints, {
    this._clock = systemClock,
    this._timeout = AppTimeouts.network,
  });

  final http.Client _httpClient;
  final OidcEndpoints _endpoints;
  final Clock _clock;
  final Duration _timeout;

  Future<Result<TokenSet>> exchangeCode({
    required String code,
    required String verifier,
    required Uri redirectUri,
  }) {
    return _requestTokens({
      OidcParameters.grantType: OidcValues.authorizationCodeGrant,
      OidcParameters.clientId: _endpoints.clientId,
      OidcParameters.code: code,
      OidcParameters.codeVerifier: verifier,
      OidcParameters.redirectUri: '$redirectUri',
    });
  }

  Future<Result<TokenSet>> refresh(String refreshToken) {
    return _requestTokens({
      OidcParameters.grantType: OidcValues.refreshTokenGrant,
      OidcParameters.clientId: _endpoints.clientId,
      OidcParameters.refreshToken: refreshToken,
    });
  }

  Future<Result<TokenSet>> _requestTokens(Map<String, String> form) async {
    try {
      final issuedAt = _clock();
      final response = await _httpClient.post(_endpoints.token, body: form).timeout(_timeout);
      final status = response.statusCode;
      if (HttpStatusCodes.isServerError(status)) {
        return Result.err(ServerFailure(statusCode: status));
      }
      if (!HttpStatusCodes.isSuccess(status)) {
        return const Result.err(AuthenticationFailure(AuthenticationFailureReason.tokenExchange));
      }
      final json = JsonMap.parse(jsonDecode(utf8.decode(response.bodyBytes)));
      return Result.ok(TokenSet.fromTokenResponse(json, issuedAt: issuedAt));
    } on FormatException {
      return const Result.err(UnexpectedResponseFailure());
    } on TimeoutException {
      return const Result.err(NetworkFailure());
    } on http.ClientException {
      return const Result.err(NetworkFailure());
    }
  }
}
