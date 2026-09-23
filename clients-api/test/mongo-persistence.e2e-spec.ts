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
  SEED_DISABLED_MESSAGE,
  SEED_MESSAGE,
} from '../src/clients/infrastructure/mongo/mongo-clients.initializer';
import { MongoClientRepository } from '../src/clients/infrastructure/mongo/mongo-client.repository';
import { AppConfig } from '../src/config/app-config';
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
import { createOutboxStore, OutboxIndexes } from '../src/shared/outbox/outbox.module';
import {
  isIndexOptionsConflict,
  MongoOutboxStore,
  RETENTION_INDEX_NAME,
} from '../src/shared/outbox/outbox.store';
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
    return `event-${String(sequence).padStart(4, '0')}`;
  };

  const initialize = async (appConfig: AppConfig = config): Promise<void> => {
    await new MongoClientsInitializer(
      database,
      appConfig,
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

  const outboxCollection = (): ReturnType<Db['collection']> =>
    database.collection(OUTBOX_COLLECTION);

  const outbox = (): Promise<OutboxDocument[]> =>
    database.collection<OutboxDocument>(OUTBOX_COLLECTION).find().toArray();

  beforeAll(async () => {
    client = await connectMongo(settings);
    database = databaseOf(client, settings);
    await new OutboxIndexes(new MongoOutboxStore(database), config).onModuleInit();
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
      expect(logger.info).toHaveBeenCalledWith({ inserted: 8, skipped: 6 }, SEED_MESSAGE);
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
      expect(logger.info).toHaveBeenLastCalledWith({ inserted: 0, skipped: 6 }, SEED_MESSAGE);
    });

    it('should_only_ensure_indexes_when_seeding_is_disabled', async () => {
      await initialize({ ...config, seedEnabled: false });

      expect(logger.info).toHaveBeenLastCalledWith(SEED_DISABLED_MESSAGE);
    });
  });

  describe('MongoClientRepository', () => {
    beforeAll(async () => {
      await outboxCollection().deleteMany({});
    });

    it('should_update_and_write_the_contract_event_to_the_outbox_atomically', async () => {
      const updated = await repository(() => 'event-atomic').update({
        clientId: 'CLI-99821',
        changes: { segment: Segment.RETAIL },
        precondition: { acceptedVersions: [7, 1] },
      });

      expect(updated).toMatchObject({ id: 'CLI-99821', segment: Segment.RETAIL, version: 2 });
      await expect(repository().findById('CLI-99821')).resolves.toEqual(updated);
      const entry = (await outbox()).find((document) => document._id === 'event-atomic');
      expect(entry).toMatchObject({
        topic: 'clients.changed.v1',
        key: 'CLI-99821',
        version: 2,
        status: OutboxStatus.PENDING,
        attempts: 0,
        createdAt: NOW,
        availableAt: NOW,
      });
      expect(createContractValidators().clientChanged(entry?.payload)).toBe(true);
      expect(entry?.payload).not.toHaveProperty('name');
    });

    it('should_not_bump_the_version_or_write_events_for_no_op_updates', async () => {
      const before = (await outbox()).length;

      const unchanged = await repository().update({
        clientId: 'CLI-10002',
        changes: { status: ClientStatus.ACTIVE },
        precondition: { acceptedVersions: [1] },
      });

      expect(unchanged).toMatchObject({ id: 'CLI-10002', version: 1 });
      expect(await outbox()).toHaveLength(before);
    });

    it('should_reject_preconditions_that_do_not_list_the_current_version', async () => {
      await expect(
        repository().update({
          clientId: 'CLI-10002',
          changes: { status: ClientStatus.BLOCKED },
          precondition: { acceptedVersions: [] },
        }),
      ).rejects.toEqual(new ClientVersionConflictError('CLI-10002', 1));
    });

    it('should_reject_unknown_clients', async () => {
      await expect(repository().update({ clientId: 'CLI-404', changes: {} })).rejects.toEqual(
        new ClientNotFoundError('CLI-404'),
      );
    });

    it('should_roll_back_the_client_change_when_the_outbox_write_fails', async () => {
      await expect(
        repository(() => 'event-atomic').update({
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
    const statusOf = async (id: string): Promise<OutboxDocument | undefined> =>
      (await outbox()).find((document) => document._id === id);

    const change = async (clientId: string, status: ClientStatus): Promise<void> => {
      const createdAt = new Date(NOW.getTime() + sequence);
      await repository(nextId, () => createdAt).update({ clientId, changes: { status } });
    };

    beforeAll(async () => {
      await outboxCollection().deleteMany({});
      await change('CLI-10002', ClientStatus.BLOCKED);
      await change('CLI-10002', ClientStatus.ACTIVE);
      await change('CLI-40002', ClientStatus.BLOCKED);
      await change('CLI-50002', ClientStatus.BLOCKED);
    });

    it('should_report_an_empty_backlog_age_when_nothing_is_pending', async () => {
      const empty = createOutboxStore(client.db(`${settings.database}_empty`), () => now);

      await expect(empty.oldestPendingCreatedAt()).resolves.toBeUndefined();
      await expect(empty.countPending()).resolves.toBe(0);
    });

    it('should_lease_only_the_oldest_unpublished_version_per_key', async () => {
      const first = await store().claim('relay-a', 2, 1000);
      const second = await store().claim('relay-b', 10, 1000);

      expect(first.map((message) => [message.key, message.version])).toEqual([
        ['CLI-10002', 2],
        ['CLI-40002', 2],
      ]);
      expect(second.map((message) => [message.key, message.version])).toEqual([['CLI-50002', 2]]);
      await expect(store().claim('relay-c', 10, 1000)).resolves.toEqual([]);
      await expect(store().countPending()).resolves.toBe(4);
      await expect(store().oldestPendingCreatedAt()).resolves.toEqual(
        (await outbox()).map((document) => document.createdAt).sort((a, b) => +a - +b)[0],
      );
    });

    it('should_fence_marking_by_lease_owner_and_then_release_the_next_version', async () => {
      const ids = (await outbox()).map((document) => document._id);

      await expect(store().markPublished('relay-z', ids)).resolves.toBe(0);
      await expect(store().markPublished('relay-a', ids)).resolves.toBe(2);
      const next = await store().claim('relay-c', 10, 1000);

      expect(next.map((message) => [message.key, message.version])).toEqual([['CLI-10002', 3]]);
      await expect(store().countPending()).resolves.toBe(2);
    });

    it('should_back_off_released_entries_and_count_the_attempt', async () => {
      const pending = (await outbox()).filter((document) => document.leaseOwner === 'relay-b');
      const ids = pending.map((document) => document._id);

      await store().release({
        owner: 'relay-b',
        ids,
        reason: 'Error: broker down',
        availableAt: new Date(now.getTime() + 1000),
      });

      expect(await statusOf(ids[0] ?? '')).toMatchObject({
        status: OutboxStatus.PENDING,
        attempts: 1,
        lastError: 'Error: broker down',
      });
      expect(await statusOf(ids[0] ?? '')).not.toHaveProperty('leaseOwner');
      await expect(store().claim('relay-d', 10, 1000)).resolves.toEqual([]);
      now = new Date(now.getTime() + 1000);
      const retried = await store().claim('relay-d', 10, 1000);
      expect(retried.map((message) => message.id)).toEqual(ids);
    });

    it('should_let_another_relay_take_over_an_expired_lease', async () => {
      now = new Date(now.getTime() + 2000);

      const reclaimed = await store().claim('relay-e', 10, 1000);

      expect(reclaimed.map((message) => [message.key, message.version])).toEqual([
        ['CLI-10002', 3],
        ['CLI-50002', 2],
      ]);
    });

    it('should_hand_each_candidate_to_a_single_relay_when_claiming_concurrently', async () => {
      now = new Date(now.getTime() + 2000);

      const [left, right] = await Promise.all([
        store().claim('relay-left', 10, 1000),
        store().claim('relay-right', 10, 1000),
      ]);

      expect(left.length + right.length).toBe(2);
      expect(new Set([...left, ...right].map((message) => message.key))).toEqual(
        new Set(['CLI-10002', 'CLI-50002']),
      );
    });
  });

  describe('outbox retention index', () => {
    const retentionOf = async (): Promise<unknown> => {
      const indexes = await outboxCollection().indexes();
      return indexes.find((index) => index.name === RETENTION_INDEX_NAME)?.expireAfterSeconds;
    };

    it('should_expire_published_entries_and_follow_retention_changes', async () => {
      await expect(retentionOf()).resolves.toBe(3600);

      await new MongoOutboxStore(database).ensureIndexes(7200);

      await expect(retentionOf()).resolves.toBe(7200);
    });

    it('should_only_treat_index_option_conflicts_as_recoverable', async () => {
      const fresh = client.db(`${settings.database}_fresh`);

      await expect(new MongoOutboxStore(fresh).ensureIndexes(-1)).rejects.toThrow(
        /expireAfterSeconds/,
      );
      expect(isIndexOptionsConflict(new Error('other'))).toBe(false);
      await fresh.dropDatabase();
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
