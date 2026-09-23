import { Client } from '../domain/client';

export const CLIENT_CHANGE_EVENTS = Symbol('CLIENT_CHANGE_EVENTS');

export interface ClientChangedEvent {
  readonly eventId: string;
  readonly occurredAt: string;
  readonly clientId: string;
  readonly version: number;
  readonly status: string;
  readonly segment: string;
  readonly taxRegime: string;
  readonly market: string;
}

export interface ClientChangeEvents {
  append(event: ClientChangedEvent): void;
}

export class DiscardedClientChangeEvents implements ClientChangeEvents {
  append(): void {
    return undefined;
  }
}

export function clientChangedEvent(
  client: Client,
  eventId: string,
  occurredAt: Date,
): ClientChangedEvent {
  return {
    eventId,
    occurredAt: occurredAt.toISOString(),
    clientId: client.id,
    version: client.version,
    status: client.status,
    segment: client.segment,
    taxRegime: client.taxRegime,
    market: client.market,
  };
}
