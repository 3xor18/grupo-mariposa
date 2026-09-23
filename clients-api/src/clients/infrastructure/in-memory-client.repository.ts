import { IdGenerator, uuidV7 } from '../../shared/ids/uuid-v7';
import { Clock, systemClock } from '../../shared/time/clock';
import { ClientChangeEvents, clientChangedEvent } from '../application/client-changed.event';
import { ClientRepository } from '../application/client.repository';
import { Client, planUpdate, UpdateClientCommand } from '../domain/client';
import { ClientNotFoundError } from '../domain/client-not-found.error';

export class InMemoryClientRepository implements ClientRepository {
  private readonly clients: Map<string, Client>;

  constructor(
    seed: readonly Client[],
    private readonly events: ClientChangeEvents,
    private readonly ids: IdGenerator = () => uuidV7(),
    private readonly clock: Clock = systemClock,
  ) {
    this.clients = new Map(seed.map((client) => [client.id, client]));
  }

  findById(id: string): Promise<Client | null> {
    return Promise.resolve(this.clients.get(id) ?? null);
  }

  async update(command: UpdateClientCommand): Promise<Client> {
    const current = await this.findById(command.clientId);
    if (current === null) {
      throw new ClientNotFoundError(command.clientId);
    }
    const plan = planUpdate(current, command);
    if (plan.changed) {
      this.clients.set(plan.client.id, plan.client);
      this.events.append(clientChangedEvent(plan.client, this.ids(), this.clock()));
    }
    return plan.client;
  }
}
