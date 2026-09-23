import { ClientSession, Collection, Db, MongoClient } from 'mongodb';
import { IdGenerator } from '../../../shared/ids/uuid-v7';
import {
  OUTBOX_COLLECTION,
  OutboxDocument,
  pendingOutboxDocument,
} from '../../../shared/outbox/outbox.document';
import { Clock } from '../../../shared/time/clock';
import { clientChangedEvent } from '../../application/client-changed.event';
import { ClientRepository } from '../../application/client.repository';
import { Client, UpdateClientCommand } from '../../domain/client';
import { ClientNotFoundError } from '../../domain/client-not-found.error';
import { ClientVersionConflictError } from '../../domain/client-version-conflict.error';
import { CLIENTS_COLLECTION, ClientDocument, toClient } from './client.document';

export interface ChangeEventSettings {
  readonly topic: string;
  readonly ids: IdGenerator;
  readonly clock: Clock;
}

const WITHOUT_ID = Object.freeze({ _id: 0 } as const);

export class MongoClientRepository implements ClientRepository {
  constructor(
    private readonly client: MongoClient,
    private readonly database: Db,
    private readonly events: ChangeEventSettings,
  ) {}

  private get clients(): Collection<ClientDocument> {
    return this.database.collection<ClientDocument>(CLIENTS_COLLECTION);
  }

  private get outbox(): Collection<OutboxDocument> {
    return this.database.collection<OutboxDocument>(OUTBOX_COLLECTION);
  }

  async findById(id: string): Promise<Client | null> {
    const document = await this.clients.findOne({ clientId: id }, { projection: WITHOUT_ID });
    return document === null ? null : toClient(document);
  }

  async update(command: UpdateClientCommand): Promise<Client> {
    const session = this.client.startSession();
    try {
      return await session.withTransaction(() => this.updateInSession(command, session));
    } finally {
      await session.endSession();
    }
  }

  private async updateInSession(
    command: UpdateClientCommand,
    session: ClientSession,
  ): Promise<Client> {
    const now = this.events.clock();
    const updated = await this.clients.findOneAndUpdate(
      this.versionedFilter(command),
      { $set: { ...command.changes, updatedAt: now }, $inc: { version: 1 } },
      { session, returnDocument: 'after', projection: WITHOUT_ID },
    );
    if (updated === null) {
      throw command.expectedVersion === undefined
        ? new ClientNotFoundError(command.clientId)
        : await this.rejection(command.clientId, command.expectedVersion, session);
    }
    const client = toClient(updated);
    const event = clientChangedEvent(client, this.events.ids(), now);
    const message = { id: event.eventId, topic: this.events.topic, key: client.id, payload: event };
    await this.outbox.insertOne(pendingOutboxDocument(message, now), { session });
    return client;
  }

  private versionedFilter(command: UpdateClientCommand): Partial<ClientDocument> {
    const { clientId, expectedVersion } = command;
    return expectedVersion === undefined ? { clientId } : { clientId, version: expectedVersion };
  }

  private async rejection(
    clientId: string,
    expectedVersion: number,
    session: ClientSession,
  ): Promise<Error> {
    const current = await this.clients.findOne({ clientId }, { session });
    return current === null
      ? new ClientNotFoundError(clientId)
      : new ClientVersionConflictError(clientId, expectedVersion, current.version);
  }
}
