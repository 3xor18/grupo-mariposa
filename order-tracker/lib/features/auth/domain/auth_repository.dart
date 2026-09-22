import 'package:order_tracker/core/result/result.dart';
import 'package:order_tracker/features/auth/domain/auth_user.dart';

abstract interface class AuthRepository {
  Stream<void> get sessionExpired;

  Future<Result<AuthUser?>> restoreSession();

  Future<void> login();

  Future<void> logout();
}
