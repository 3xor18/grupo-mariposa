import 'package:equatable/equatable.dart';

sealed class AppFailure extends Equatable {
  const AppFailure({this.traceId});

  final String? traceId;

  bool get isRetryable;

  @override
  List<Object?> get props => [traceId];
}

final class NotFoundFailure extends AppFailure {
  const NotFoundFailure({super.traceId});

  @override
  bool get isRetryable => false;
}

final class ValidationFailure extends AppFailure {
  const ValidationFailure({this.fieldErrors = const [], super.traceId});

  final List<FieldError> fieldErrors;

  @override
  bool get isRetryable => false;

  @override
  List<Object?> get props => [fieldErrors, traceId];
}

final class UnauthorizedFailure extends AppFailure {
  const UnauthorizedFailure({super.traceId});

  @override
  bool get isRetryable => false;
}

final class ForbiddenFailure extends AppFailure {
  const ForbiddenFailure({super.traceId});

  @override
  bool get isRetryable => false;
}

final class RateLimitedFailure extends AppFailure {
  const RateLimitedFailure({this.retryAfter, super.traceId});

  final Duration? retryAfter;

  @override
  bool get isRetryable => true;

  @override
  List<Object?> get props => [retryAfter, traceId];
}

final class ServerFailure extends AppFailure {
  const ServerFailure({required this.statusCode, super.traceId});

  final int statusCode;

  @override
  bool get isRetryable => true;

  @override
  List<Object?> get props => [statusCode, traceId];
}

final class NetworkFailure extends AppFailure {
  const NetworkFailure();

  @override
  bool get isRetryable => true;
}

final class UnexpectedResponseFailure extends AppFailure {
  const UnexpectedResponseFailure({super.traceId});

  @override
  bool get isRetryable => true;
}

final class AuthenticationFailure extends AppFailure {
  const AuthenticationFailure(this.reason);

  final AuthenticationFailureReason reason;

  @override
  bool get isRetryable => true;

  @override
  List<Object?> get props => [reason];
}

enum AuthenticationFailureReason { callbackRejected, stateMismatch, tokenExchange, sessionExpired }

final class FieldError extends Equatable {
  const FieldError({required this.field, required this.message});

  final String field;
  final String message;

  @override
  List<Object?> get props => [field, message];
}
