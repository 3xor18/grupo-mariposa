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

export const INTERNAL_ERROR_DETAIL = ERROR_CATALOG[ErrorCode.INTERNAL_ERROR].detail;

function isServerError(status: number): boolean {
  return status >= SERVER_ERROR_STATUS_THRESHOLD;
}

function fromCode(
  code: ErrorCode,
  detail: string,
  options: ProblemOptions = {},
): ProblemDescriptor {
  const definition = ERROR_CATALOG[code];
  return {
    ...(options.errors === undefined ? {} : { errors: options.errors }),
    ...(options.headers === undefined ? {} : { headers: options.headers }),
    ...(options.cause === undefined ? {} : { cause: options.cause }),
    status: definition.status,
    code,
    title: definition.title,
    detail,
    unexpected: false,
  };
}

function fallbackCodeFor(status: number): ErrorCode {
  return isServerError(status) ? ErrorCode.INTERNAL_ERROR : ErrorCode.BAD_REQUEST;
}

function safeDetail(code: ErrorCode, status: number, message: string): string {
  return isServerError(status) ? ERROR_CATALOG[code].detail : message;
}

function fromHttpException(exception: HttpException): ProblemDescriptor {
  const status = exception.getStatus();
  const knownCode = HTTP_STATUS_ERROR_CODES.get(status);
  const code = knownCode ?? fallbackCodeFor(status);
  const detail = safeDetail(code, status, exception.message);
  if (knownCode !== undefined) {
    return { ...fromCode(knownCode, detail), unexpected: isServerError(status) };
  }
  return {
    status,
    code,
    title: STATUS_CODES[status] ?? ERROR_CATALOG[code].title,
    detail,
    unexpected: isServerError(status),
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
