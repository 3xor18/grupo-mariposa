import 'package:order_tracker/core/error/app_failure.dart';
import 'package:order_tracker/core/error/problem_details.dart';
import 'package:order_tracker/core/http/http_status_codes.dart';

abstract final class FailureMapper {
  static AppFailure fromResponse({
    required int statusCode,
    ProblemDetails? problem,
    String? retryAfter,
  }) {
    final traceId = problem?.traceId;
    return switch (statusCode) {
      HttpStatusCodes.badRequest => ValidationFailure(
        fieldErrors: problem?.errors ?? const [],
        traceId: traceId,
      ),
      HttpStatusCodes.unauthorized => UnauthorizedFailure(traceId: traceId),
      HttpStatusCodes.forbidden => ForbiddenFailure(traceId: traceId),
      HttpStatusCodes.notFound when problem != null => NotFoundFailure(traceId: traceId),
      HttpStatusCodes.tooManyRequests => RateLimitedFailure(
        retryAfter: _parseRetryAfter(retryAfter),
        traceId: traceId,
      ),
      final status when HttpStatusCodes.isServerError(status) => ServerFailure(
        statusCode: status,
        traceId: traceId,
      ),
      int() => UnexpectedResponseFailure(traceId: traceId),
    };
  }

  static Duration? _parseRetryAfter(String? header) {
    final seconds = header == null ? null : int.tryParse(header.trim());
    return seconds == null ? null : Duration(seconds: seconds);
  }
}
