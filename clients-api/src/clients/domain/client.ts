import { ClientStatus } from './client-status.enum';
import { Segment } from './segment.enum';
import { TaxRegime } from './tax-regime.enum';

export const INITIAL_CLIENT_VERSION = 1;

export interface Client {
  readonly id: string;
  readonly name: string;
  readonly status: ClientStatus;
  readonly segment: Segment;
  readonly taxRegime: TaxRegime;
  readonly market: string;
  readonly version: number;
}

export interface ClientChanges {
  readonly status?: ClientStatus;
  readonly segment?: Segment;
  readonly taxRegime?: TaxRegime;
}

export interface UpdateClientCommand {
  readonly clientId: string;
  readonly changes: ClientChanges;
  readonly expectedVersion?: number;
}

export function applyChanges(client: Client, changes: ClientChanges): Client {
  return { ...client, ...changes, version: client.version + 1 };
}
