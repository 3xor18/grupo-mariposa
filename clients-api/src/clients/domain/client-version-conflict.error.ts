import { DomainError } from '../../shared/errors/domain.error';
import { ErrorCode } from '../../shared/errors/error-code.enum';

export class ClientVersionConflictError extends DomainError {
  readonly code = ErrorCode.PRECONDITION_FAILED;

  constructor(
    readonly clientId: string,
    readonly currentVersion: number,
  ) {
    super(
      `Client ${clientId} is at version ${String(currentVersion)}, which If-Match does not match`,
    );
  }
}
