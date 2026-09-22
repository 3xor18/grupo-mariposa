import { ErrorCode } from './error-code.enum';

export interface FieldError {
  readonly field: string;
  readonly message: string;
}

export interface ProblemOptions {
  readonly errors?: readonly FieldError[];
  readonly headers?: Readonly<Record<string, string>>;
}

export class ProblemException extends Error {
  constructor(
    readonly code: ErrorCode,
    readonly detail: string,
    readonly options: ProblemOptions = {},
  ) {
    super(detail);
    this.name = ProblemException.name;
  }
}
