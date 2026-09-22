import 'package:equatable/equatable.dart';

final class AuthUser extends Equatable {
  const AuthUser({this.name, this.username, this.email});

  final String? name;
  final String? username;
  final String? email;

  String? get displayName => name ?? username ?? email;

  @override
  List<Object?> get props => [name, username, email];
}
