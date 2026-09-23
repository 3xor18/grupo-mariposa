import { DomainError } from '../../shared/errors/domain.error';
import { ErrorCode } from '../../shared/errors/error-code.enum';

export class ClientNotFoundError extends DomainError {
  readonly code = ErrorCode.CLIENT_NOT_FOUND;

  constructor(readonly clientId: string) {
    super(`Client ${clientId} does not exist`);
  }
}
