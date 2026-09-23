import { Request } from 'express';

export interface Principal {
  readonly id: string;
}

export interface AuthenticatedRequest extends Request {
  principal?: Principal;
}
