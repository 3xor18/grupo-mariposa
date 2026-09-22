abstract final class AuthStorageKeys {
  static const verifier = 'order-tracker.auth.pkce-verifier';
  static const state = 'order-tracker.auth.state';
  static const nonce = 'order-tracker.auth.nonce';
  static const mode = 'order-tracker.auth.mode';

  static const List<String> all = [verifier, state, nonce, mode];
}

abstract final class AuthRedirectModes {
  static const interactive = 'interactive';
  static const silent = 'silent';
}
