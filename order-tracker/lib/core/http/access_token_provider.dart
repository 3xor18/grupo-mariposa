abstract interface class AccessTokenProvider {
  Future<String?> validAccessToken();

  void onUnauthorized();
}
