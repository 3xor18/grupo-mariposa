import { PinoLogger } from 'nestjs-pino';
import { testConfig } from '../../../test/support/test-app';
import { MetricsRegistry } from '../observability/metrics-registry';
import { ChangeEventPublisher } from './change-event-publisher';
import { OutboxMetrics } from './outbox-metrics';
import { OutboxMessage } from './outbox.document';
import { OutboxStore } from './outbox.store';
import { OutboxRelay, RELAY_MESSAGES } from './outbox-relay';

const NOW = new Date('2026-09-23T10:00:00.000Z');

const MESSAGES: OutboxMessage[] = [
  { id: 'e1', topic: 't', key: 'CLI-1', version: 2, payload: {} },
  { id: 'e2', topic: 't', key: 'CLI-2', version: 5, payload: {} },
];

interface RelayFixture {
  readonly relay: OutboxRelay;
  readonly store: jest.Mocked<OutboxStore>;
  readonly publisher: jest.Mocked<ChangeEventPublisher>;
  readonly logger: { warn: jest.Mock; error: jest.Mock };
  readonly registry: MetricsRegistry;
}

const wait = (milliseconds: number): Promise<void> =>
  new Promise((resolve) => setTimeout(resolve, milliseconds));

function setup(bootstrapServers: string[] = ['localhost:9092']): RelayFixture {
  const base = testConfig('http://unused');
  const config = {
    ...base,
    kafka: { ...base.kafka, bootstrapServers },
    outbox: { ...base.outbox, relayIntervalMs: 5, batchSize: 2, leaseMs: 1000, retryDelayMs: 250 },
  };
  const store: jest.Mocked<OutboxStore> = {
    claim: jest.fn().mockResolvedValue([]),
    markPublished: jest.fn(),
    release: jest.fn().mockResolvedValue(undefined),
    countPending: jest.fn().mockResolvedValue(0),
    oldestPendingCreatedAt: jest.fn().mockResolvedValue(undefined),
  };
  const publisher: jest.Mocked<ChangeEventPublisher> = {
    publish: jest.fn().mockResolvedValue(undefined),
    close: jest.fn().mockResolvedValue(undefined),
  };
  const logger = { warn: jest.fn(), error: jest.fn() };
  const registry = new MetricsRegistry();
  const relay = new OutboxRelay(
    config,
    store,
    publisher,
    new OutboxMetrics(registry, store, () => NOW),
    () => 'relay-id',
    () => NOW,
    logger as unknown as PinoLogger,
  );
  return { relay, store, publisher, logger, registry };
}

describe('OutboxRelay', () => {
  it('should_identify_itself_with_host_process_and_unique_id', () => {
    expect(setup().relay.owner).toMatch(/^.+:\d+:relay-id$/);
  });

  it('should_claim_publish_and_mark_a_batch_with_its_lease_owner', async () => {
    const { relay, store, publisher, registry } = setup();
    store.claim.mockResolvedValueOnce(MESSAGES);
    store.markPublished.mockResolvedValueOnce(2);

    await expect(relay.relayOnce()).resolves.toBe(2);

    expect(store.claim).toHaveBeenCalledWith(relay.owner, 2, 1000);
    expect(publisher.publish).toHaveBeenCalledWith(MESSAGES);
    expect(store.markPublished).toHaveBeenCalledWith(relay.owner, ['e1', 'e2']);
    expect(await registry.render()).toContain('outbox_published_total 2');
  });

  it('should_do_nothing_when_no_entries_are_pending', async () => {
    const { relay, publisher } = setup();

    await expect(relay.relayOnce()).resolves.toBe(0);
    expect(publisher.publish).not.toHaveBeenCalled();
  });

  it('should_release_the_lease_and_count_failures_when_publishing_fails', async () => {
    const { relay, store, publisher, logger, registry } = setup();
    store.claim.mockResolvedValueOnce(MESSAGES);
    publisher.publish.mockRejectedValueOnce(new Error('broker down'));

    await expect(relay.relayOnce()).resolves.toBe(0);

    expect(store.release).toHaveBeenCalledWith({
      owner: relay.owner,
      ids: ['e1', 'e2'],
      reason: 'Error: broker down',
      availableAt: new Date('2026-09-23T10:00:00.250Z'),
    });
    expect(store.markPublished).not.toHaveBeenCalled();
    expect(logger.warn).toHaveBeenCalledWith(
      expect.objectContaining({ count: 2 }),
      RELAY_MESSAGES.publishFailed,
    );
    expect(await registry.render()).toContain('outbox_publish_failures_total 2');
  });

  it('should_warn_when_another_relay_took_over_part_of_the_batch', async () => {
    const { relay, store, logger } = setup();
    store.claim.mockResolvedValueOnce(MESSAGES);
    store.markPublished.mockResolvedValueOnce(1);

    await expect(relay.relayOnce()).resolves.toBe(1);
    expect(logger.warn).toHaveBeenCalledWith({ expected: 2, marked: 1 }, RELAY_MESSAGES.leaseLost);
  });

  it('should_not_start_when_kafka_is_not_configured', () => {
    const { relay, store, logger } = setup([]);

    relay.onApplicationBootstrap();

    expect(logger.warn).toHaveBeenCalledWith(RELAY_MESSAGES.disabled);
    expect(store.claim).not.toHaveBeenCalled();
  });

  it('should_poll_periodically_log_failures_and_stop_cleanly', async () => {
    const { relay, store, publisher, logger } = setup();
    store.claim.mockRejectedValueOnce(new Error('mongo down'));

    relay.onApplicationBootstrap();
    await wait(60);
    await relay.beforeApplicationShutdown();
    const calls = store.claim.mock.calls.length;
    await wait(30);

    expect(calls).toBeGreaterThan(1);
    expect(store.claim).toHaveBeenCalledTimes(calls);
    expect(logger.error).toHaveBeenCalledWith(
      { err: new Error('mongo down') },
      RELAY_MESSAGES.tickFailed,
    );
    expect(publisher.close).toHaveBeenCalledTimes(1);
  });

  it('should_finish_the_running_batch_before_closing_the_publisher', async () => {
    const { relay, store, publisher } = setup();
    const release: { resolve: () => void } = { resolve: () => undefined };
    store.claim.mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          release.resolve = () => {
            resolve([]);
          };
        }),
    );

    relay.onApplicationBootstrap();
    await wait(20);
    const stopping = relay.beforeApplicationShutdown();
    release.resolve();
    await stopping;
    await wait(20);

    expect(store.claim).toHaveBeenCalledTimes(1);
    expect(publisher.close).toHaveBeenCalledTimes(1);
  });
});
