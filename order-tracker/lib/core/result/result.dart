import 'package:equatable/equatable.dart';
import 'package:order_tracker/core/error/app_failure.dart';

sealed class Result<T> extends Equatable {
  const Result();

  const factory Result.ok(T value) = Ok<T>;

  const factory Result.err(AppFailure failure) = Err<T>;

  R fold<R>({required R Function(T value) onOk, required R Function(AppFailure failure) onErr}) {
    return switch (this) {
      Ok<T>(:final value) => onOk(value),
      Err<T>(:final failure) => onErr(failure),
    };
  }

  Result<R> map<R>(R Function(T value) transform) {
    return switch (this) {
      Ok<T>(:final value) => Result<R>.ok(transform(value)),
      Err<T>(:final failure) => Result<R>.err(failure),
    };
  }
}

final class Ok<T> extends Result<T> {
  const Ok(this.value);

  final T value;

  @override
  List<Object?> get props => [value];
}

final class Err<T> extends Result<T> {
  const Err(this.failure);

  final AppFailure failure;

  @override
  List<Object?> get props => [failure];
}
