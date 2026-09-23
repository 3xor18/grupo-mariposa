import { aClient } from '../../../test/support/clients';
import { MarketCatalog, parseMarkets } from '../../shared/markets/market-catalog';
import { ClientNotFoundError } from '../domain/client-not-found.error';
import { ClientStatus } from '../domain/client-status.enum';
import { ClientVersionConflictError } from '../domain/client-version-conflict.error';
import { Segment } from '../domain/segment.enum';
import { CLIENT_SEED, DEMO_CACHE_CLIENTS, seedForCatalog } from './client.seed';
import { InMemoryClientRepository } from './in-memory-client.repository';

const NOW = new Date('2026-09-23T10:00:00.000Z');

describe('InMemoryClientRepository', () => {
  const repository = (): InMemoryClientRepository =>
    new InMemoryClientRepository(
      [aClient()],
      () => 'event-1',
      () => NOW,
    );

  it('should_find_seeded_client_by_id_and_return_null_otherwise', async () => {
    const clients = repository();

    await expect(clients.findById('CLI-1')).resolves.toEqual(aClient());
    await expect(clients.findById('cli-1')).resolves.toBeNull();
  });

  it('should_apply_changes_bump_version_and_record_the_change_event', async () => {
    const clients = repository();

    const updated = await clients.update({
      clientId: 'CLI-1',
      changes: { status: ClientStatus.BLOCKED },
      expectedVersion: 1,
    });

    expect(updated).toEqual(aClient({ status: ClientStatus.BLOCKED, version: 2 }));
    await expect(clients.findById('CLI-1')).resolves.toEqual(updated);
    expect(clients.events).toEqual([
      {
        eventId: 'event-1',
        occurredAt: '2026-09-23T10:00:00.000Z',
        clientId: 'CLI-1',
        version: 2,
        status: 'BLOCKED',
        segment: 'RETAIL',
        taxRegime: 'EXEMPT',
        market: 'PE',
        name: 'Test client',
      },
    ]);
  });

  it('should_update_unconditionally_when_no_version_is_expected', async () => {
    const clients = repository();

    await clients.update({ clientId: 'CLI-1', changes: { segment: Segment.WHOLESALE } });
    const second = await clients.update({ clientId: 'CLI-1', changes: {} });

    expect(second).toMatchObject({ segment: Segment.WHOLESALE, version: 3 });
  });

  it('should_reject_stale_versions_without_recording_events', async () => {
    const clients = repository();

    await expect(
      clients.update({ clientId: 'CLI-1', changes: {}, expectedVersion: 5 }),
    ).rejects.toEqual(new ClientVersionConflictError('CLI-1', 5, 1));
    expect(clients.events).toEqual([]);
  });

  it('should_reject_unknown_clients', async () => {
    await expect(repository().update({ clientId: 'CLI-404', changes: {} })).rejects.toEqual(
      new ClientNotFoundError('CLI-404'),
    );
  });

  it('should_use_uuid_v7_and_system_clock_by_default', async () => {
    const clients = new InMemoryClientRepository([aClient()]);

    await clients.update({ clientId: 'CLI-1', changes: {} });

    expect(clients.events[0]?.eventId).toMatch(/^[\da-f]{8}-[\da-f]{4}-7[\da-f]{3}-/);
  });
});

describe('client seed', () => {
  it('should_cover_five_markets_with_unique_ids_and_initial_versions', () => {
    const ids = new Set(CLIENT_SEED.map((client) => client.id));
    const markets = [...new Set(CLIENT_SEED.map((client) => client.market))].sort((a, b) =>
      a.localeCompare(b),
    );

    expect(ids.size).toBe(CLIENT_SEED.length);
    expect(markets).toEqual(['CL', 'CO', 'EC', 'MX', 'PE']);
    expect(CLIENT_SEED.every((client) => client.version === 1)).toBe(true);
    expect(DEMO_CACHE_CLIENTS.map((client) => client.id)).toEqual(['CLI-70001']);
  });

  it('should_only_seed_clients_whose_market_is_in_the_catalog', () => {
    const catalog = new MarketCatalog(parseMarkets('MX:MXN:es-MX,CL:CLP:es-CL'));

    const seeded = seedForCatalog(catalog);

    expect(new Set(seeded.map((client) => client.market))).toEqual(new Set(['MX', 'CL']));
    expect(seeded.map((client) => client.id)).toContain('CLI-50001');
    expect(seeded.map((client) => client.id)).not.toContain('CLI-20001');
  });
});
