import 'package:order_tracker/core/config/app_config.dart';
import 'package:order_tracker/features/auth/data/pkce.dart';

abstract final class OidcParameters {
  static const responseType = 'response_type';
  static const clientId = 'client_id';
  static const redirectUri = 'redirect_uri';
  static const scope = 'scope';
  static const state = 'state';
  static const codeChallenge = 'code_challenge';
  static const codeChallengeMethod = 'code_challenge_method';
  static const code = 'code';
  static const codeVerifier = 'code_verifier';
  static const grantType = 'grant_type';
  static const refreshToken = 'refresh_token';
  static const idTokenHint = 'id_token_hint';
  static const postLogoutRedirectUri = 'post_logout_redirect_uri';
  static const nonce = 'nonce';
  static const prompt = 'prompt';
  static const error = 'error';
}

abstract final class OidcValues {
  static const codeResponseType = 'code';
  static const scope = 'openid profile email';
  static const authorizationCodeGrant = 'authorization_code';
  static const refreshTokenGrant = 'refresh_token';
  static const silentPrompt = 'none';
}

final class OidcEndpoints {
  const OidcEndpoints({required this.issuer, required this.clientId});

  factory OidcEndpoints.fromConfig(AppConfig config) {
    return OidcEndpoints(issuer: config.issuerUri, clientId: config.clientId);
  }

  static const _protocolPath = ['protocol', 'openid-connect'];
  static const _authorizationPath = 'auth';
  static const _tokenPath = 'token';
  static const _logoutPath = 'logout';

  final Uri issuer;
  final String clientId;

  Uri get token => _endpoint(_tokenPath);

  Uri authorization({
    required Uri redirectUri,
    required String state,
    required String codeChallenge,
    required String nonce,
    String? prompt,
  }) {
    return _endpoint(_authorizationPath).replace(
      queryParameters: {
        OidcParameters.responseType: OidcValues.codeResponseType,
        OidcParameters.clientId: clientId,
        OidcParameters.redirectUri: '$redirectUri',
        OidcParameters.scope: OidcValues.scope,
        OidcParameters.state: state,
        OidcParameters.codeChallenge: codeChallenge,
        OidcParameters.codeChallengeMethod: PkceGenerator.challengeMethod,
        OidcParameters.nonce: nonce,
        OidcParameters.prompt: ?prompt,
      },
    );
  }

  Uri logout({required Uri redirectUri, String? idTokenHint}) {
    return _endpoint(_logoutPath).replace(
      queryParameters: {
        OidcParameters.clientId: clientId,
        OidcParameters.postLogoutRedirectUri: '$redirectUri',
        OidcParameters.idTokenHint: ?idTokenHint,
      },
    );
  }

  Uri _endpoint(String name) {
    final segments = issuer.pathSegments.where((segment) => segment.isNotEmpty);
    return issuer.replace(pathSegments: [...segments, ..._protocolPath, name]);
  }
}
