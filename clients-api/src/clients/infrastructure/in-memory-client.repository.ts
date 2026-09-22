import { ClientRepository } from '../application/client.repository';
import { Client } from '../domain/client';

export class InMemoryClientRepository implements ClientRepository {
  private readonly clients: ReadonlyMap<string, Client>;

  constructor(seed: readonly Client[]) {
    this.clients = new Map(seed.map((client) => [client.id, client]));
  }

  findById(id: string): Promise<Client | null> {
    return Promise.resolve(this.clients.get(id) ?? null);
  }
}
