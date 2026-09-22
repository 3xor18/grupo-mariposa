import { HttpStatus } from '@nestjs/common';
import { ErrorCode } from './error-code.enum';

export const SERVER_ERROR_STATUS_THRESHOLD: number = HttpStatus.INTERNAL_SERVER_ERROR;

export interface ErrorDefinition {
  readonly status: HttpStatus;
  readonly title: string;
}

export const ERROR_CATALOG: Readonly<Record<ErrorCode, ErrorDefinition>> = {
  [ErrorCode.VALIDATION_ERROR]: { status: HttpStatus.BAD_REQUEST, title: 'Validation failed' },
  [ErrorCode.BAD_REQUEST]: { status: HttpStatus.BAD_REQUEST, title: 'Bad request' },
  [ErrorCode.UNAUTHORIZED]: { status: HttpStatus.UNAUTHORIZED, title: 'Unauthorized' },
  [ErrorCode.FORBIDDEN]: { status: HttpStatus.FORBIDDEN, title: 'Forbidden' },
  [ErrorCode.CLIENT_NOT_FOUND]: { status: HttpStatus.NOT_FOUND, title: 'Client not found' },
  [ErrorCode.NOT_FOUND]: { status: HttpStatus.NOT_FOUND, title: 'Resource not found' },
  [ErrorCode.RATE_LIMITED]: { status: HttpStatus.TOO_MANY_REQUESTS, title: 'Too many requests' },
  [ErrorCode.INTERNAL_ERROR]: {
    status: HttpStatus.INTERNAL_SERVER_ERROR,
    title: 'Internal server error',
  },
  [ErrorCode.BAD_GATEWAY]: { status: HttpStatus.BAD_GATEWAY, title: 'Bad gateway' },
  [ErrorCode.SERVICE_UNAVAILABLE]: {
    status: HttpStatus.SERVICE_UNAVAILABLE,
    title: 'Service unavailable',
  },
};

export const HTTP_STATUS_ERROR_CODES: ReadonlyMap<number, ErrorCode> = new Map([
  [HttpStatus.BAD_REQUEST, ErrorCode.BAD_REQUEST],
  [HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED],
  [HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN],
  [HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND],
  [HttpStatus.TOO_MANY_REQUESTS, ErrorCode.RATE_LIMITED],
  [HttpStatus.BAD_GATEWAY, ErrorCode.BAD_GATEWAY],
  [HttpStatus.SERVICE_UNAVAILABLE, ErrorCode.SERVICE_UNAVAILABLE],
]);
