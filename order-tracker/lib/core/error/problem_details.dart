import 'package:equatable/equatable.dart';
import 'package:order_tracker/core/error/app_failure.dart';
import 'package:order_tracker/core/json/json_map.dart';

abstract final class _ProblemFields {
  static const status = 'status';
  static const code = 'code';
  static const title = 'title';
  static const detail = 'detail';
  static const traceId = 'traceId';
  static const errors = 'errors';
  static const field = 'field';
  static const message = 'message';
}

final class ProblemDetails extends Equatable {
  const ProblemDetails({
    required this.status,
    this.code,
    this.title,
    this.detail,
    this.traceId,
    this.errors = const [],
  });

  factory ProblemDetails.fromJson(JsonMap json) {
    return ProblemDetails(
      status: json.requireInt(_ProblemFields.status),
      code: json.optionalString(_ProblemFields.code),
      title: json.optionalString(_ProblemFields.title),
      detail: json.optionalString(_ProblemFields.detail),
      traceId: json.optionalString(_ProblemFields.traceId),
      errors: json.objectList(_ProblemFields.errors).map(_fieldError).toList(growable: false),
    );
  }

  final int status;
  final String? code;
  final String? title;
  final String? detail;
  final String? traceId;
  final List<FieldError> errors;

  static FieldError _fieldError(JsonMap json) {
    return FieldError(
      field: json.requireString(_ProblemFields.field),
      message: json.requireString(_ProblemFields.message),
    );
  }

  @override
  List<Object?> get props => [status, code, title, detail, traceId, errors];
}
