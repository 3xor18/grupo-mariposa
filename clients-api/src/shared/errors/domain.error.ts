import { ErrorCode } from './error-code.enum';

export abstract class DomainError extends Error {
  abstract readonly code: ErrorCode;

  protected constructor(message: string) {
    super(message);
    this.name = new.target.name;
  }
}
