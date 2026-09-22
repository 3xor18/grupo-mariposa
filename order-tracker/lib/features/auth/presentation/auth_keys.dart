import 'package:flutter/widgets.dart';

abstract final class AuthKeys {
  static const checkingView = Key('authChecking');
  static const loginPage = Key('loginPage');
  static const loginButton = Key('loginButton');
  static const loginError = Key('loginError');
  static const logoutButton = Key('logoutButton');
  static const userName = Key('signedInUserName');
}
