import 'package:flutter_test/flutter_test.dart';
import 'package:order_tracker/core/error/app_failure.dart';
import 'package:order_tracker/core/error/failure_mapper.dart';
import 'package:order_tracker/core/error/problem_details.dart';
import 'package:order_tracker/core/json/json_map.dart';

void main() {
  const traceId = 'trace-1';

  ProblemDetails problem(int status, String code) {
    return ProblemDetails(status: status, code: code, traceId: traceId);
  }

  group('ProblemDetails', () {
    test('should parse a full problem+json body', () {
      final parsed = ProblemDetails.fromJson(
        const JsonMap({
          'type': 'https://contracts.grupomariposa.dev/problems/validation',
          'title': 'Validation failed',
          'status': 400,
          'code': 'VALIDATION_ERROR',
          'detail': 'size must be <= 100',
          'instance': '/orders',
          'traceId': traceId,
          'timestamp': '2026-09-18T15:42:12Z',
          'errors': [
            {'field': 'size', 'message': 'must be <= 100'},
          ],
        }),
      );
      expect(parsed.status, 400);
      expect(parsed.code, 'VALIDATION_ERROR');
      expect(parsed.title, 'Validation failed');
      expect(parsed.detail, 'size must be <= 100');
      expect(parsed.errors, const [FieldError(field: 'size', message: 'must be <= 100')]);
    });

    test('should tolerate missing optional members', () {
      final parsed = ProblemDetails.fromJson(const JsonMap({'status': 404}));
      expect(parsed, const ProblemDetails(status: 404));
    });
  });

  group('FailureMapper', () {
    test('should map validation errors with field details', () {
      const errors = [FieldError(field: 'size', message: 'too big')];
      final failure = FailureMapper.fromResponse(
        statusCode: 400,
        problem: const ProblemDetails(status: 400, errors: errors, traceId: traceId),
      );
      expect(failure, const ValidationFailure(fieldErrors: errors, traceId: traceId));
      expect(FailureMapper.fromResponse(statusCode: 400), const ValidationFailure());
    });

    test('should map authentication and authorization errors', () {
      expect(
        FailureMapper.fromResponse(statusCode: 401, problem: problem(401, 'UNAUTHORIZED')),
        const UnauthorizedFailure(traceId: traceId),
      );
      expect(
        FailureMapper.fromResponse(statusCode: 403, problem: problem(403, 'FORBIDDEN')),
        const ForbiddenFailure(traceId: traceId),
      );
    });

    test('should map not found only when the body is a problem', () {
      expect(
        FailureMapper.fromResponse(statusCode: 404, problem: problem(404, 'ORDER_NOT_FOUND')),
        const NotFoundFailure(traceId: traceId),
      );
      expect(FailureMapper.fromResponse(statusCode: 404), const UnexpectedResponseFailure());
    });

    test('should map rate limiting with retry after seconds', () {
      expect(
        FailureMapper.fromResponse(statusCode: 429, retryAfter: ' 3 '),
        const RateLimitedFailure(retryAfter: Duration(seconds: 3)),
      );
      expect(
        FailureMapper.fromResponse(statusCode: 429, retryAfter: 'soon'),
        const RateLimitedFailure(),
      );
      expect(FailureMapper.fromResponse(statusCode: 429), const RateLimitedFailure());
    });

    test('should map server errors and unexpected statuses', () {
      expect(
        FailureMapper.fromResponse(statusCode: 503, problem: problem(503, 'SERVICE_UNAVAILABLE')),
        const ServerFailure(statusCode: 503, traceId: traceId),
      );
      expect(FailureMapper.fromResponse(statusCode: 418), const UnexpectedResponseFailure());
    });
  });
}
