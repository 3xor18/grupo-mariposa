import { Client } from '../domain/client';
import { ClientResponse } from './client.response';

export function toClientResponse(client: Client): ClientResponse {
  return new ClientResponse({
    version: client.version,
    clientId: client.id,
    name: client.name,
    status: client.status,
    segment: client.segment,
    taxRegime: client.taxRegime,
    market: client.market,
  });
}
