import { uuidV7, IdGenerator } from '../../shared/ids/uuid-v7';
import { Clock, systemClock } from '../../shared/time/clock';
import { ClientChangedEvent, clientChangedEvent } from '../application/client-changed.event';
import { ClientRepository } from '../application/client.repository';
import { applyChanges, Client, UpdateClientCommand } from '../domain/client';
import { ClientNotFoundError } from '../domain/client-not-found.error';
import { ClientVersionConflictError } from '../domain/client-version-conflict.error';

export class InMemoryClientRepository implements ClientRepository {
  private readonly clients: Map<string, Client>;
  private readonly recorded: ClientChangedEvent[] = [];

  constructor(
    seed: readonly Client[],
    private readonly ids: IdGenerator = () => uuidV7(),
    private readonly clock: Clock = systemClock,
  ) {
    this.clients = new Map(seed.map((client) => [client.id, client]));
  }

  get events(): readonly ClientChangedEvent[] {
    return [...this.recorded];
  }

  findById(id: string): Promise<Client | null> {
    return Promise.resolve(this.clients.get(id) ?? null);
  }

  update(command: UpdateClientCommand): Promise<Client> {
    const current = this.clients.get(command.clientId);
    if (current === undefined) {
      return Promise.reject(new ClientNotFoundError(command.clientId));
    }
    const { expectedVersion } = command;
    if (expectedVersion !== undefined && expectedVersion !== current.version) {
      return Promise.reject(
        new ClientVersionConflictError(command.clientId, expectedVersion, current.version),
      );
    }
    const updated = applyChanges(current, command.changes);
    this.clients.set(updated.id, updated);
    this.recorded.push(clientChangedEvent(updated, this.ids(), this.clock()));
    return Promise.resolve(updated);
  }
}
