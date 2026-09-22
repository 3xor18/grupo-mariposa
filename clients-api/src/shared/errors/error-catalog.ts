import { HttpStatus } from '@nestjs/common';
import { ErrorCode } from './error-code.enum';

export const SERVER_ERROR_STATUS_THRESHOLD: number = HttpStatus.INTERNAL_SERVER_ERROR;

export interface ErrorDefinition {
  readonly status: HttpStatus;
  readonly title: string;
  readonly detail: string;
}

function definition(status: HttpStatus, title: string, detail: string): ErrorDefinition {
  return Object.freeze({ status, title, detail });
}

export const ERROR_CATALOG: Readonly<Record<ErrorCode, ErrorDefinition>> = Object.freeze({
  [ErrorCode.VALIDATION_ERROR]: definition(
    HttpStatus.BAD_REQUEST,
    'Validation failed',
    'The request contains invalid parameters',
  ),
  [ErrorCode.BAD_REQUEST]: definition(
    HttpStatus.BAD_REQUEST,
    'Bad request',
    'The request could not be processed',
  ),
  [ErrorCode.UNAUTHORIZED]: definition(
    HttpStatus.UNAUTHORIZED,
    'Unauthorized',
    'Authentication is required',
  ),
  [ErrorCode.FORBIDDEN]: definition(
    HttpStatus.FORBIDDEN,
    'Forbidden',
    'The caller is not allowed to perform this operation',
  ),
  [ErrorCode.CLIENT_NOT_FOUND]: definition(
    HttpStatus.NOT_FOUND,
    'Client not found',
    'The requested client does not exist',
  ),
  [ErrorCode.NOT_FOUND]: definition(
    HttpStatus.NOT_FOUND,
    'Resource not found',
    'The requested resource does not exist',
  ),
  [ErrorCode.METHOD_NOT_ALLOWED]: definition(
    HttpStatus.METHOD_NOT_ALLOWED,
    'Method not allowed',
    'The HTTP method is not supported by this resource',
  ),
  [ErrorCode.RATE_LIMITED]: definition(
    HttpStatus.TOO_MANY_REQUESTS,
    'Too many requests',
    'Request rate limit exceeded, retry later',
  ),
  [ErrorCode.INTERNAL_ERROR]: definition(
    HttpStatus.INTERNAL_SERVER_ERROR,
    'Internal server error',
    'An unexpected error occurred',
  ),
  [ErrorCode.BAD_GATEWAY]: definition(
    HttpStatus.BAD_GATEWAY,
    'Bad gateway',
    'An upstream dependency returned an invalid response',
  ),
  [ErrorCode.SERVICE_UNAVAILABLE]: definition(
    HttpStatus.SERVICE_UNAVAILABLE,
    'Service unavailable',
    'The service is temporarily unavailable',
  ),
});

export const HTTP_STATUS_ERROR_CODES: ReadonlyMap<number, ErrorCode> = new Map([
  [HttpStatus.BAD_REQUEST, ErrorCode.BAD_REQUEST],
  [HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED],
  [HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN],
  [HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND],
  [HttpStatus.METHOD_NOT_ALLOWED, ErrorCode.METHOD_NOT_ALLOWED],
  [HttpStatus.TOO_MANY_REQUESTS, ErrorCode.RATE_LIMITED],
  [HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR],
  [HttpStatus.BAD_GATEWAY, ErrorCode.BAD_GATEWAY],
  [HttpStatus.SERVICE_UNAVAILABLE, ErrorCode.SERVICE_UNAVAILABLE],
]);
