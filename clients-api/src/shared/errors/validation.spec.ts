import { ValidationPipe } from '@nestjs/common';
import { ValidationError } from 'class-validator';
import { ErrorCode } from './error-code.enum';
import {
  createValidationPipe,
  flattenValidationErrors,
  toValidationProblem,
  VALIDATION_DETAIL,
} from './validation';

function validationError(
  property: string,
  constraints?: Record<string, string>,
  children: ValidationError[] = [],
): ValidationError {
  return Object.assign(new ValidationError(), { property, constraints, children });
}

describe('validation', () => {
  it('should_flatten_nested_constraints_with_dotted_field_paths', () => {
    const errors = [
      validationError('clientId', { matches: 'bad format', isString: 'not a string' }),
      validationError('address', undefined, [validationError('city', { isNotEmpty: 'empty' })]),
    ];

    expect(flattenValidationErrors(errors)).toEqual([
      { field: 'clientId', message: 'bad format' },
      { field: 'clientId', message: 'not a string' },
      { field: 'address.city', message: 'empty' },
    ]);
  });

  it('should_handle_errors_without_children', () => {
    const error = Object.assign(new ValidationError(), { property: 'x', constraints: { a: 'b' } });

    expect(flattenValidationErrors([error])).toEqual([{ field: 'x', message: 'b' }]);
  });

  it('should_create_validation_problem_with_field_errors', () => {
    const problem = toValidationProblem([validationError('clientId', { matches: 'bad' })]);

    expect(problem.code).toBe(ErrorCode.VALIDATION_ERROR);
    expect(problem.detail).toBe(VALIDATION_DETAIL);
    expect(problem.options.errors).toEqual([{ field: 'clientId', message: 'bad' }]);
  });

  it('should_create_strict_validation_pipe', () => {
    expect(createValidationPipe()).toBeInstanceOf(ValidationPipe);
  });
});
