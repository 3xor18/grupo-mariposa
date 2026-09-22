import 'package:equatable/equatable.dart';
import 'package:order_tracker/features/auth/data/oidc_endpoints.dart';

sealed class AuthorizationCallback extends Equatable {
  const AuthorizationCallback();

  factory AuthorizationCallback.parse(Uri uri) {
    final parameters = uri.queryParameters;
    final error = parameters[OidcParameters.error];
    if (error != null) {
      return AuthorizationDenied(error);
    }
    final code = parameters[OidcParameters.code];
    if (code == null) {
      return const NoAuthorizationCallback();
    }
    return AuthorizationGranted(code: code, state: parameters[OidcParameters.state]);
  }

  static Uri stripFrom(Uri uri) => Uri.parse('${uri.origin}${uri.path}');

  @override
  List<Object?> get props => [];
}

final class NoAuthorizationCallback extends AuthorizationCallback {
  const NoAuthorizationCallback();
}

final class AuthorizationGranted extends AuthorizationCallback {
  const AuthorizationGranted({required this.code, required this.state});

  final String code;
  final String? state;

  @override
  List<Object?> get props => [code, state];
}

final class AuthorizationDenied extends AuthorizationCallback {
  const AuthorizationDenied(this.error);

  final String error;

  @override
  List<Object?> get props => [error];
}
