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
import { Client, ClientChanges, planUpdate, UpdateClientCommand } from '../../domain/client';
import { ClientNotFoundError } from '../../domain/client-not-found.error';
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
    const current = await this.clients.findOne(
      { clientId: command.clientId },
      { session, projection: WITHOUT_ID },
    );
    if (current === null) {
      throw new ClientNotFoundError(command.clientId);
    }
    const plan = planUpdate(toClient(current), command);
    if (plan.changed) {
      await this.persist(plan.client, plan.changes, session);
    }
    return plan.client;
  }

  private async persist(
    client: Client,
    changes: ClientChanges,
    session: ClientSession,
  ): Promise<void> {
    const now = this.events.clock();
    await this.clients.updateOne(
      { clientId: client.id, version: client.version - 1 },
      { $set: { ...changes, updatedAt: now }, $inc: { version: 1 } },
      { session },
    );
    const event = clientChangedEvent(client, this.events.ids(), now);
    const message = {
      id: event.eventId,
      topic: this.events.topic,
      key: client.id,
      version: client.version,
      payload: event,
    };
    await this.outbox.insertOne(pendingOutboxDocument(message, now), { session });
  }
}
