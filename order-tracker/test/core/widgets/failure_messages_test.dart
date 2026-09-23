import 'package:flutter_test/flutter_test.dart';
import 'package:order_tracker/core/error/app_failure.dart';
import 'package:order_tracker/core/l10n/app_strings.dart';
import 'package:order_tracker/core/widgets/failure_messages.dart';

void main() {
  test('should describe every failure in Spanish', () {
    const expectations = <(AppFailure, String)>[
      (NetworkFailure(), AppStrings.networkError),
      (ServerFailure(statusCode: 500), AppStrings.serverError),
      (RateLimitedFailure(), AppStrings.rateLimitedError),
      (UnauthorizedFailure(), AppStrings.unauthorizedError),
      (ForbiddenFailure(), AppStrings.forbiddenError),
      (ValidationFailure(), AppStrings.validationError),
      (UnexpectedResponseFailure(), AppStrings.unexpectedError),
      (NotFoundFailure(), AppStrings.notFoundError),
      (
        AuthenticationFailure(AuthenticationFailureReason.sessionExpired),
        AppStrings.unauthorizedError,
      ),
      (
        AuthenticationFailure(AuthenticationFailureReason.callbackRejected),
        AppStrings.loginFailedError,
      ),
    ];
    for (final (failure, message) in expectations) {
      expect(FailureMessages.of(failure), message);
    }
  });

  test('should add retry timing or the support code as detail', () {
    expect(
      FailureMessages.detailOf(const RateLimitedFailure(retryAfter: Duration(seconds: 3))),
      AppStrings.retryAfter(3),
    );
    expect(
      FailureMessages.detailOf(const ServerFailure(statusCode: 503, traceId: 'abc')),
      AppStrings.supportCode('abc'),
    );
    expect(FailureMessages.detailOf(const RateLimitedFailure()), isNull);
    expect(FailureMessages.detailOf(const NetworkFailure()), isNull);
  });
}
