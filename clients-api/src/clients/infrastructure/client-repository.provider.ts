import { Provider } from '@nestjs/common';
import { Db, MongoClient } from 'mongodb';
import { APP_CONFIG, AppConfig, StorageDriver } from '../../config/app-config';
import { MARKET_CATALOG, MarketCatalog } from '../../shared/markets/market-catalog';
import { ID_GENERATOR, IdGenerator } from '../../shared/ids/uuid-v7';
import { MONGO_CLIENT, MONGO_DATABASE } from '../../shared/mongo/mongo.tokens';
import { CLOCK, Clock } from '../../shared/time/clock';
import { DiscardedClientChangeEvents } from '../application/client-changed.event';
import { CLIENT_REPOSITORY, ClientRepository } from '../application/client.repository';
import { seedForCatalog } from './client.seed';
import { InMemoryClientRepository } from './in-memory-client.repository';
import { MongoClientRepository } from './mongo/mongo-client.repository';
import { MongoClientsInitializer } from './mongo/mongo-clients.initializer';

export function createClientRepository(
  client: MongoClient,
  database: Db,
  config: AppConfig,
  ids: IdGenerator,
  clock: Clock,
): ClientRepository {
  return new MongoClientRepository(client, database, {
    topic: config.kafka.changesTopic,
    ids,
    clock,
  });
}

export const clientRepositoryProvider: Provider<ClientRepository> = {
  provide: CLIENT_REPOSITORY,
  useFactory: createClientRepository,
  inject: [MONGO_CLIENT, MONGO_DATABASE, APP_CONFIG, ID_GENERATOR, CLOCK],
};

export function createInMemoryClientRepository(
  catalog: MarketCatalog,
  config: AppConfig,
  ids: IdGenerator,
  clock: Clock,
): ClientRepository {
  const seed = config.seedEnabled ? seedForCatalog(catalog) : [];
  return new InMemoryClientRepository(seed, new DiscardedClientChangeEvents(), ids, clock);
}

export const inMemoryClientRepositoryProvider: Provider<ClientRepository> = {
  provide: CLIENT_REPOSITORY,
  useFactory: createInMemoryClientRepository,
  inject: [MARKET_CATALOG, APP_CONFIG, ID_GENERATOR, CLOCK],
};

export function storageProvidersFor(driver: StorageDriver): Provider[] {
  return driver === StorageDriver.MEMORY
    ? [inMemoryClientRepositoryProvider]
    : [clientRepositoryProvider, MongoClientsInitializer];
}
