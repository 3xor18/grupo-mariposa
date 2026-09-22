import 'package:equatable/equatable.dart';
import 'package:order_tracker/features/auth/domain/auth_user.dart';

sealed class SessionRestoration extends Equatable {
  const SessionRestoration();

  @override
  List<Object?> get props => [];
}

final class SignedIn extends SessionRestoration {
  const SignedIn(this.user);

  final AuthUser user;

  @override
  List<Object?> get props => [user];
}

final class SignedOut extends SessionRestoration {
  const SignedOut();
}

final class SigningInSilently extends SessionRestoration {
  const SigningInSilently();
}
