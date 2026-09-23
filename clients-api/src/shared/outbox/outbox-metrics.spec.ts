import { MetricsRegistry } from '../observability/metrics-registry';
import { registerUnpublishedGauge } from './outbox-metrics';
import {
  OutboxDocument,
  OutboxStatus,
  pendingOutboxDocument,
  toOutboxMessage,
} from './outbox.document';
import { OutboxStore } from './outbox.store';

describe('registerUnpublishedGauge', () => {
  const storeCounting = (count: () => Promise<number>): OutboxStore => ({
    claim: jest.fn(),
    markPublished: jest.fn(),
    release: jest.fn(),
    countUnpublished: count,
  });

  it('should_report_the_unpublished_backlog_on_scrape', async () => {
    const metrics = new MetricsRegistry();
    registerUnpublishedGauge(
      metrics.registry,
      storeCounting(() => Promise.resolve(7)),
    );

    expect(await metrics.render()).toContain('outbox_unpublished 7');
  });

  it('should_keep_serving_metrics_when_the_store_is_unavailable', async () => {
    const metrics = new MetricsRegistry();
    registerUnpublishedGauge(
      metrics.registry,
      storeCounting(() => Promise.reject(new Error('down'))),
    );

    expect(await metrics.render()).toContain('outbox_unpublished 0');
  });
});

describe('outbox documents', () => {
  it('should_create_pending_documents_and_map_them_back_to_messages', () => {
    const createdAt = new Date('2026-09-23T10:00:00.000Z');
    const message = { id: 'e1', topic: 't', key: 'CLI-1', payload: { a: 1 } };

    const document: OutboxDocument = pendingOutboxDocument(message, createdAt);

    expect(document).toEqual({
      _id: 'e1',
      topic: 't',
      key: 'CLI-1',
      payload: { a: 1 },
      status: OutboxStatus.PENDING,
      attempts: 0,
      createdAt,
    });
    expect(toOutboxMessage(document)).toEqual(message);
  });
});
