import { HttpException } from '@nestjs/common';
import { STATUS_CODES } from 'node:http';
import { DomainError } from './domain.error';
import {
  ERROR_CATALOG,
  HTTP_STATUS_ERROR_CODES,
  SERVER_ERROR_STATUS_THRESHOLD,
} from './error-catalog';
import { ErrorCode } from './error-code.enum';
import { ProblemDescriptor } from './problem-details';
import { ProblemException, ProblemOptions } from './problem.exception';

export const INTERNAL_ERROR_DETAIL = 'An unexpected error occurred';

function fromCode(
  code: ErrorCode,
  detail: string,
  options: ProblemOptions = {},
): ProblemDescriptor {
  const definition = ERROR_CATALOG[code];
  return {
    ...options,
    status: definition.status,
    code,
    title: definition.title,
    detail,
    unexpected: false,
  };
}

function fallbackCodeFor(status: number): ErrorCode {
  return status >= SERVER_ERROR_STATUS_THRESHOLD ? ErrorCode.INTERNAL_ERROR : ErrorCode.BAD_REQUEST;
}

function fromHttpException(exception: HttpException): ProblemDescriptor {
  const status = exception.getStatus();
  const knownCode = HTTP_STATUS_ERROR_CODES.get(status);
  if (knownCode !== undefined) {
    return fromCode(knownCode, exception.message);
  }
  const code = fallbackCodeFor(status);
  return {
    status,
    code,
    title: STATUS_CODES[status] ?? ERROR_CATALOG[code].title,
    detail: exception.message,
    unexpected: code === ErrorCode.INTERNAL_ERROR,
  };
}

export function describeException(exception: unknown): ProblemDescriptor {
  if (exception instanceof ProblemException) {
    return fromCode(exception.code, exception.detail, exception.options);
  }
  if (exception instanceof DomainError) {
    return fromCode(exception.code, exception.message);
  }
  if (exception instanceof HttpException) {
    return fromHttpException(exception);
  }
  return { ...fromCode(ErrorCode.INTERNAL_ERROR, INTERNAL_ERROR_DETAIL), unexpected: true };
}
