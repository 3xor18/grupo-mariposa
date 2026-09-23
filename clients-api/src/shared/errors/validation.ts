import { ValidationError, ValidationPipe } from '@nestjs/common';
import { ERROR_CATALOG } from './error-catalog';
import { ErrorCode } from './error-code.enum';
import { FieldError, ProblemException } from './problem.exception';

export const VALIDATION_DETAIL = ERROR_CATALOG[ErrorCode.VALIDATION_ERROR].detail;

const PATH_SEPARATOR = '.';

function fieldPath(parent: string | undefined, property: string): string {
  return parent === undefined ? property : `${parent}${PATH_SEPARATOR}${property}`;
}

export function flattenValidationErrors(
  errors: readonly ValidationError[],
  parent?: string,
): FieldError[] {
  return errors.flatMap((error) => {
    const field = fieldPath(parent, error.property);
    const own = Object.values(error.constraints ?? {}).map((message) => ({ field, message }));
    return [...own, ...flattenValidationErrors(error.children ?? [], field)];
  });
}

export function toValidationProblem(errors: ValidationError[]): ProblemException {
  return new ProblemException(ErrorCode.VALIDATION_ERROR, VALIDATION_DETAIL, {
    errors: flattenValidationErrors(errors),
  });
}

export function createValidationPipe(): ValidationPipe {
  return new ValidationPipe({
    whitelist: true,
    forbidNonWhitelisted: true,
    transform: true,
    exceptionFactory: toValidationProblem,
  });
}
