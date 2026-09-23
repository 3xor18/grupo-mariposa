abstract interface class AccessTokenProvider {
  Future<String?> validAccessToken();

  void onUnauthorized(String? rejectedToken);
}

final class AccessTokenUnavailableException implements Exception {
  const AccessTokenUnavailableException();
}
