abstract final class HttpStatusCodes {
  static const ok = 200;
  static const multipleChoices = 300;
  static const badRequest = 400;
  static const unauthorized = 401;
  static const forbidden = 403;
  static const notFound = 404;
  static const tooManyRequests = 429;
  static const internalServerError = 500;

  static bool isSuccess(int status) => status >= ok && status < multipleChoices;

  static bool isServerError(int status) => status >= internalServerError;
}
