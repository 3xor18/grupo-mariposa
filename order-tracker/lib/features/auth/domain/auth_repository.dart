import 'package:order_tracker/core/result/result.dart';
import 'package:order_tracker/features/auth/domain/session_restoration.dart';

abstract interface class AuthRepository {
  Stream<void> get sessionExpired;

  Future<Result<SessionRestoration>> restoreSession();

  Future<void> login();

  Future<void> logout();
}
