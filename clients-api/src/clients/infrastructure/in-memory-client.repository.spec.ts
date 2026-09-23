import { CLIENT_REPOSITORY } from '../application/client.repository';
import { ClientStatus } from '../domain/client-status.enum';
import { Market } from '../domain/market.enum';
import { CLIENT_SEED } from './client.seed';
import { clientRepositoryProvider } from './client-repository.provider';
import { InMemoryClientRepository } from './in-memory-client.repository';

describe('InMemoryClientRepository', () => {
  const repository = new InMemoryClientRepository(CLIENT_SEED);

  it('should_find_seeded_client_by_id', async () => {
    await expect(repository.findById('CLI-20002')).resolves.toEqual({
      id: 'CLI-20002',
      name: 'Tienda El Porvenir',
      market: Market.CO,
      segment: 'RETAIL',
      taxRegime: 'GENERAL',
      status: ClientStatus.BLOCKED,
    });
  });

  it('should_return_null_when_client_is_unknown', async () => {
    await expect(repository.findById('CLI-00000')).resolves.toBeNull();
  });

  it('should_be_case_sensitive_on_ids', async () => {
    await expect(repository.findById('cli-99821')).resolves.toBeNull();
  });
});

describe('client seed', () => {
  it('should_cover_at_least_six_clients_across_three_markets_with_unique_ids', () => {
    const ids = new Set(CLIENT_SEED.map((client) => client.id));
    const markets = new Set(CLIENT_SEED.map((client) => client.market));

    expect(CLIENT_SEED.length).toBeGreaterThanOrEqual(6);
    expect(ids.size).toBe(CLIENT_SEED.length);
    expect([...markets].sort((a, b) => a.localeCompare(b))).toEqual([
      Market.CO,
      Market.MX,
      Market.PE,
    ]);
  });
});

describe('clientRepositoryProvider', () => {
  it('should_bind_in_memory_adapter_to_repository_token', () => {
    const provider = clientRepositoryProvider as {
      provide: symbol;
      useFactory: () => unknown;
    };

    expect(provider.provide).toBe(CLIENT_REPOSITORY);
    expect(provider.useFactory()).toBeInstanceOf(InMemoryClientRepository);
  });
});
