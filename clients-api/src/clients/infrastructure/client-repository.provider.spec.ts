import { testConfig } from '../../../test/support/test-app';
import { StorageDriver } from '../../config/app-config';
import { MarketCatalog, parseMarkets } from '../../shared/markets/market-catalog';
import {
  clientRepositoryProvider,
  createInMemoryClientRepository,
  inMemoryClientRepositoryProvider,
  storageProvidersFor,
} from './client-repository.provider';
import { MongoClientsInitializer } from './mongo/mongo-clients.initializer';

const CATALOG = new MarketCatalog(parseMarkets('MX:MXN:es-MX'));
const NOW = new Date('2026-09-23T10:00:00.000Z');

describe('client repository providers', () => {
  it('should_seed_the_in_memory_repository_only_when_seeding_is_enabled', async () => {
    const config = testConfig('http://unused');
    const seeded = createInMemoryClientRepository(
      CATALOG,
      config,
      () => 'e',
      () => NOW,
    );
    const empty = createInMemoryClientRepository(
      CATALOG,
      { ...config, seedEnabled: false },
      () => 'e',
      () => NOW,
    );

    await expect(seeded.findById('CLI-99821')).resolves.toMatchObject({ market: 'MX' });
    await expect(empty.findById('CLI-99821')).resolves.toBeNull();
  });

  it('should_select_providers_by_storage_driver', () => {
    expect(storageProvidersFor(StorageDriver.MEMORY)).toEqual([inMemoryClientRepositoryProvider]);
    expect(storageProvidersFor(StorageDriver.MONGO)).toEqual([
      clientRepositoryProvider,
      MongoClientsInitializer,
    ]);
  });
});
