import { Db, MongoClient } from 'mongodb';
import { PinoLogger } from 'nestjs-pino';
import { ClientNotFoundError } from '../src/clients/domain/client-not-found.error';
import { ClientStatus } from '../src/clients/domain/client-status.enum';
import { ClientVersionConflictError } from '../src/clients/domain/client-version-conflict.error';
import { Segment } from '../src/clients/domain/segment.enum';
import { createClientRepository } from '../src/clients/infrastructure/client-repository.provider';
import { CLIENTS_COLLECTION } from '../src/clients/infrastructure/mongo/client.document';
import {
  MongoClientsInitializer,
  SEED_MESSAGE,
} from '../src/clients/infrastructure/mongo/mongo-clients.initializer';
import { MongoClientRepository } from '../src/clients/infrastructure/mongo/mongo-client.repository';
import { MarketCatalog, parseMarkets } from '../src/shared/markets/market-catalog';
import {
  connectMongo,
  databaseOf,
  MongoHealth,
  MongoLifecycle,
} from '../src/shared/mongo/mongo.module';
import {
  OUTBOX_COLLECTION,
  OutboxDocument,
  OutboxStatus,
} from '../src/shared/outbox/outbox.document';
import { MongoOutboxStore } from '../src/shared/outbox/outbox.store';
import { createOutboxStore, OutboxIndexes } from '../src/shared/outbox/outbox.module';
import { createContractValidators } from './support/contracts';
import { testConfig, testMongoSettings } from './support/test-app';

const NOW = new Date('2026-09-23T10:00:00.000Z');
const CATALOG = new MarketCatalog(parseMarkets('MX:MXN:es-MX,CL:CLP:es-CL'));

describe('MongoDB persistence', () => {
  const config = testConfig('http://unused');
  const settings = testMongoSettings();
  let client: MongoClient;
  let database: Db;
  let sequence = 0;
  const logger = { info: jest.fn() };
  const nextId = (): string => {
    sequence += 1;
    return `event-${String(sequence)}`;
  };

  const initialize = async (): Promise<void> => {
    await new MongoClientsInitializer(
      database,
      CATALOG,
      () => NOW,
      logger as unknown as PinoLogger,
    ).onModuleInit();
  };

  const repository = (
    ids: () => string = nextId,
    clock: () => Date = () => NOW,
  ): MongoClientRepository =>
    new MongoClientRepository(client, database, { topic: 'clients.changed.v1', ids, clock });

  const outbox = (): Promise<OutboxDocument[]> =>
    database.collection<OutboxDocument>(OUTBOX_COLLECTION).find().toArray();

  beforeAll(async () => {
    client = await connectMongo(settings);
    database = databaseOf(client, settings);
    await new OutboxIndexes(new MongoOutboxStore(database)).onModuleInit();
    await initialize();
  });

  afterAll(async () => {
    await database.dropDatabase();
    await new MongoLifecycle(client).onApplicationShutdown();
  });

  describe('seeding', () => {
    it('should_insert_only_catalog_markets_with_version_one', async () => {
      const markets = await database.collection(CLIENTS_COLLECTION).distinct('market');

      expect(new Set(markets)).toEqual(new Set(['CL', 'MX']));
      await expect(repository().findById('CLI-50001')).resolves.toMatchObject({
        market: 'CL',
        version: 1,
      });
      await expect(repository().findById('CLI-20001')).resolves.toBeNull();
      expect(logger.info).toHaveBeenCalledWith(
        expect.objectContaining({ skipped: 6 }),
        SEED_MESSAGE,
      );
    });

    it('should_keep_changes_made_through_the_api_when_seeding_again', async () => {
      await repository().update({
        clientId: 'CLI-10003',
        changes: { status: ClientStatus.BLOCKED },
      });

      await initialize();

      await expect(repository().findById('CLI-10003')).resolves.toMatchObject({
        status: ClientStatus.BLOCKED,
        version: 2,
      });
      expect(logger.info).toHaveBeenLastCalledWith(
        expect.objectContaining({ inserted: 0 }),
        SEED_MESSAGE,
      );
    });
  });

  describe('MongoClientRepository', () => {
    it('should_update_and_write_the_contract_event_to_the_outbox_atomically', async () => {
      const updated = await repository(() => 'event-atomic').update({
        clientId: 'CLI-99821',
        changes: { segment: Segment.RETAIL },
        expectedVersion: 1,
      });

      expect(updated).toMatchObject({ id: 'CLI-99821', segment: Segment.RETAIL, version: 2 });
      const entry = (await outbox()).find((document) => document._id === 'event-atomic');
      expect(entry).toMatchObject({
        topic: 'clients.changed.v1',
        key: 'CLI-99821',
        status: OutboxStatus.PENDING,
        attempts: 0,
        createdAt: NOW,
      });
      expect(createContractValidators().clientChanged(entry?.payload)).toBe(true);
      expect(entry?.payload).toMatchObject({ clientId: 'CLI-99821', version: 2, status: 'ACTIVE' });
    });

    it('should_reject_stale_versions_with_the_current_version', async () => {
      await expect(
        repository().update({ clientId: 'CLI-10002', changes: {}, expectedVersion: 9 }),
      ).rejects.toEqual(new ClientVersionConflictError('CLI-10002', 9, 1));
    });

    it.each([undefined, 1])('should_reject_unknown_clients_%p', async (expectedVersion) => {
      const command = { clientId: 'CLI-404', changes: {} };

      await expect(
        repository().update(
          expectedVersion === undefined ? command : { ...command, expectedVersion },
        ),
      ).rejects.toEqual(new ClientNotFoundError('CLI-404'));
    });

    it('should_roll_back_the_client_change_when_the_outbox_write_fails', async () => {
      const duplicate = (): string => 'event-atomic';

      await expect(
        repository(duplicate).update({
          clientId: 'CLI-40001',
          changes: { status: ClientStatus.BLOCKED },
        }),
      ).rejects.toThrow(/duplicate key/);
      await expect(repository().findById('CLI-40001')).resolves.toMatchObject({
        status: ClientStatus.ACTIVE,
        version: 1,
      });
    });

    it('should_be_built_by_the_provider_factory', () => {
      expect(createClientRepository(client, database, config, nextId, () => NOW)).toBeInstanceOf(
        MongoClientRepository,
      );
    });
  });

  describe('MongoOutboxStore', () => {
    let now = new Date('2026-09-23T11:00:00.000Z');
    const store = (): MongoOutboxStore => createOutboxStore(database, () => now);

    beforeAll(async () => {
      await database.collection(OUTBOX_COLLECTION).deleteMany({});
      const clientIds = ['CLI-10002', 'CLI-40002', 'CLI-50002'];
      for (const [index, clientId] of clientIds.entries()) {
        const createdAt = new Date(NOW.getTime() + index);
        await repository(nextId, () => createdAt).update({ clientId, changes: {} });
      }
    });

    it('should_lease_batches_in_creation_order_to_a_single_owner', async () => {
      const first = await store().claim('relay-a', 2, 1000);
      const second = await store().claim('relay-b', 5, 1000);

      expect(first.map((message) => message.key)).toEqual(['CLI-10002', 'CLI-40002']);
      expect(second.map((message) => message.key)).toEqual(['CLI-50002']);
      await expect(store().claim('relay-b', 5, 1000)).resolves.toEqual([]);
      await expect(store().countUnpublished()).resolves.toBe(3);
    });

    it('should_fence_marking_by_lease_owner', async () => {
      const ids = (await outbox()).map((document) => document._id);

      await expect(store().markPublished('relay-z', ids)).resolves.toBe(0);
      await expect(store().markPublished('relay-a', ids)).resolves.toBe(2);
      await expect(store().countUnpublished()).resolves.toBe(1);
    });

    it('should_release_failed_entries_and_reclaim_expired_leases', async () => {
      const pending = (await outbox()).filter(
        (document) => document.status !== OutboxStatus.PUBLISHED,
      );
      const ids = pending.map((document) => document._id);

      await store().release('relay-b', ids, 'Error: broker down');
      const released = (await outbox()).find((document) => document._id === ids[0]);
      expect(released).toMatchObject({
        status: OutboxStatus.PENDING,
        lastError: 'Error: broker down',
      });
      expect(released).not.toHaveProperty('leaseOwner');

      await store().claim('relay-c', 5, 1000);
      await expect(store().claim('relay-d', 5, 1000)).resolves.toEqual([]);
      now = new Date(now.getTime() + 2000);
      const reclaimed = await store().claim('relay-d', 5, 1000);
      expect(reclaimed.map((message) => message.id)).toEqual(ids);
      expect((await outbox()).find((document) => document._id === ids[0])?.attempts).toBe(3);
    });
  });

  describe('MongoHealth', () => {
    it('should_report_healthy_while_connected_and_unhealthy_after_close', async () => {
      const probeClient = await connectMongo(settings);
      const health = new MongoHealth(databaseOf(probeClient, settings));

      await expect(health.isHealthy()).resolves.toBe(true);
      await probeClient.close();
      await expect(health.isHealthy()).resolves.toBe(false);
    });
  });
});
