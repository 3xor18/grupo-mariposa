import 'package:order_tracker/core/error/app_failure.dart';
import 'package:order_tracker/core/l10n/app_strings.dart';

abstract final class FailureMessages {
  static String of(AppFailure failure) {
    return switch (failure) {
      NetworkFailure() => AppStrings.networkError,
      ServerFailure() => AppStrings.serverError,
      RateLimitedFailure() => AppStrings.rateLimitedError,
      UnauthorizedFailure() => AppStrings.unauthorizedError,
      ForbiddenFailure() => AppStrings.forbiddenError,
      ValidationFailure() => AppStrings.validationError,
      UnexpectedResponseFailure() => AppStrings.unexpectedError,
      NotFoundFailure() => AppStrings.notFoundError,
      AuthenticationFailure(reason: AuthenticationFailureReason.sessionExpired) =>
        AppStrings.unauthorizedError,
      AuthenticationFailure() => AppStrings.loginFailedError,
    };
  }

  static String? detailOf(AppFailure failure) {
    final retryAfter = failure is RateLimitedFailure ? failure.retryAfter : null;
    if (retryAfter != null) {
      return AppStrings.retryAfter(retryAfter.inSeconds);
    }
    final traceId = failure.traceId;
    return traceId == null ? null : AppStrings.supportCode(traceId);
  }
}
