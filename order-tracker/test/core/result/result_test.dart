import 'package:flutter_test/flutter_test.dart';
import 'package:order_tracker/core/error/app_failure.dart';
import 'package:order_tracker/core/result/result.dart';

void main() {
  group('Result', () {
    test('should fold ok values', () {
      const result = Result<int>.ok(2);
      expect(result.fold(onOk: (value) => value * 2, onErr: (_) => 0), 4);
      expect(result, const Ok(2));
    });

    test('should fold failures', () {
      const result = Result<int>.err(NotFoundFailure());
      expect(result.fold(onOk: (_) => 'ok', onErr: (failure) => 'err'), 'err');
      expect(result, const Err<int>(NotFoundFailure()));
    });

    test('should map ok values and keep failures', () {
      expect(const Result<int>.ok(2).map((value) => '$value'), const Ok('2'));
      expect(
        const Result<int>.err(NetworkFailure()).map((value) => '$value'),
        const Err<String>(NetworkFailure()),
      );
    });
  });

  group('AppFailure', () {
    test('should flag retryable failures', () {
      const retryable = [
        RateLimitedFailure(),
        ServerFailure(statusCode: 503),
        NetworkFailure(),
        UnexpectedResponseFailure(),
        AuthenticationFailure(AuthenticationFailureReason.tokenExchange),
      ];
      const terminal = [
        NotFoundFailure(),
        ValidationFailure(),
        UnauthorizedFailure(),
        ForbiddenFailure(),
      ];
      expect(retryable.every((failure) => failure.isRetryable), isTrue);
      expect(terminal.any((failure) => failure.isRetryable), isFalse);
    });

    test('should compare failures by value', () {
      const error = FieldError(field: 'page', message: 'must be positive');
      expect(
        const ValidationFailure(fieldErrors: [error], traceId: 't'),
        const ValidationFailure(fieldErrors: [error], traceId: 't'),
      );
      expect(
        const RateLimitedFailure(retryAfter: Duration(seconds: 1)),
        isNot(const RateLimitedFailure()),
      );
      expect(const NotFoundFailure(traceId: 'a'), isNot(const NotFoundFailure(traceId: 'b')));
      expect(
        const AuthenticationFailure(AuthenticationFailureReason.stateMismatch).props,
        [AuthenticationFailureReason.stateMismatch],
      );
    });
  });
}
