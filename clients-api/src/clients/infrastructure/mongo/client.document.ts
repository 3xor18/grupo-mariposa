import { Client } from '../../domain/client';
import { ClientStatus } from '../../domain/client-status.enum';
import { Segment } from '../../domain/segment.enum';
import { TaxRegime } from '../../domain/tax-regime.enum';

export const CLIENTS_COLLECTION = 'clients';

export interface ClientDocument {
  readonly clientId: string;
  readonly name: string;
  readonly status: ClientStatus;
  readonly segment: Segment;
  readonly taxRegime: TaxRegime;
  readonly market: string;
  readonly version: number;
  readonly updatedAt: Date;
}

export function toClientDocument(client: Client, updatedAt: Date): ClientDocument {
  return {
    clientId: client.id,
    name: client.name,
    status: client.status,
    segment: client.segment,
    taxRegime: client.taxRegime,
    market: client.market,
    version: client.version,
    updatedAt,
  };
}

export function toClient(document: ClientDocument): Client {
  return {
    id: document.clientId,
    name: document.name,
    status: document.status,
    segment: document.segment,
    taxRegime: document.taxRegime,
    market: document.market,
    version: document.version,
  };
}
