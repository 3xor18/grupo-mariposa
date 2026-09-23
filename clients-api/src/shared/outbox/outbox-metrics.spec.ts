import { MetricsRegistry } from '../observability/metrics-registry';
import {
  ageInSeconds,
  OUTBOX_METRIC_NAMES,
  OutboxMetrics,
  registerBacklogGauges,
} from './outbox-metrics';
import {
  OutboxDocument,
  OutboxStatus,
  pendingOutboxDocument,
  toOutboxMessage,
} from './outbox.document';
import { OutboxStore } from './outbox.store';

const NOW = new Date('2026-09-23T10:00:30.000Z');

function storeWith(
  countPending: () => Promise<number>,
  oldestPendingCreatedAt: () => Promise<Date | undefined>,
): OutboxStore {
  return {
    claim: jest.fn(),
    markPublished: jest.fn(),
    release: jest.fn(),
    countPending,
    oldestPendingCreatedAt,
  };
}

describe('outbox metrics', () => {
  it('should_use_the_platform_metric_names', () => {
    expect(OUTBOX_METRIC_NAMES).toEqual({
      published: 'outbox_published_total',
      failures: 'outbox_publish_failures_total',
      pending: 'outbox_pending',
      oldestAge: 'outbox_oldest_age_seconds',
    });
  });

  it('should_report_backlog_size_and_oldest_age_on_scrape', async () => {
    const metrics = new MetricsRegistry();
    const createdAt = new Date('2026-09-23T10:00:00.000Z');
    registerBacklogGauges(
      metrics.registry,
      storeWith(
        () => Promise.resolve(7),
        () => Promise.resolve(createdAt),
      ),
      () => NOW,
    );

    const output = await metrics.render();

    expect(output).toContain('\noutbox_pending 7\n');
    expect(output).toContain('\noutbox_oldest_age_seconds 30\n');
  });

  it('should_report_zero_age_for_an_empty_backlog_and_survive_store_failures', async () => {
    const metrics = new MetricsRegistry();
    registerBacklogGauges(
      metrics.registry,
      storeWith(
        () => Promise.reject(new Error('down')),
        () => Promise.resolve(undefined),
      ),
      () => NOW,
    );

    const output = await metrics.render();

    expect(output).toContain('\noutbox_pending 0\n');
    expect(output).toContain('\noutbox_oldest_age_seconds 0\n');
  });

  it('should_compute_age_in_seconds', () => {
    expect(ageInSeconds(new Date('2026-09-23T10:00:29.500Z'), NOW)).toBe(0.5);
    expect(ageInSeconds(undefined, NOW)).toBe(0);
  });

  it('should_count_published_and_failed_events', async () => {
    const metrics = new MetricsRegistry();
    const store = storeWith(
      () => Promise.resolve(0),
      () => Promise.resolve(undefined),
    );
    const outbox = new OutboxMetrics(metrics, store, () => NOW);

    outbox.recordPublished(3);
    outbox.recordFailure(2);
    const output = await metrics.render();

    expect(output).toContain('\noutbox_published_total 3\n');
    expect(output).toContain('\noutbox_publish_failures_total 2\n');
  });
});

describe('outbox documents', () => {
  it('should_create_available_pending_documents_and_map_them_back_to_messages', () => {
    const createdAt = new Date('2026-09-23T10:00:00.000Z');
    const message = { id: 'e1', topic: 't', key: 'CLI-1', version: 2, payload: { a: 1 } };

    const document: OutboxDocument = pendingOutboxDocument(message, createdAt);

    expect(document).toEqual({
      _id: 'e1',
      topic: 't',
      key: 'CLI-1',
      version: 2,
      payload: { a: 1 },
      status: OutboxStatus.PENDING,
      attempts: 0,
      createdAt,
      availableAt: createdAt,
    });
    expect(toOutboxMessage(document)).toEqual(message);
  });
});
