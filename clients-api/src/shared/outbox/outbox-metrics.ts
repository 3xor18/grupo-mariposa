import { Inject, Injectable } from '@nestjs/common';
import { Counter, Gauge, Registry } from 'prom-client';
import { MetricsRegistry } from '../observability/metrics-registry';
import { OUTBOX_STORE, OutboxStore } from './outbox.store';

export const OUTBOX_METRIC_NAMES = Object.freeze({
  published: 'outbox_published_total',
  failures: 'outbox_publish_failures_total',
  unpublished: 'outbox_unpublished',
});

export function registerUnpublishedGauge(registry: Registry, store: OutboxStore): Gauge {
  const unpublished: Gauge = new Gauge({
    name: OUTBOX_METRIC_NAMES.unpublished,
    help: 'Outbox entries not yet published',
    registers: [registry],
    collect: async (): Promise<void> => {
      await store.countUnpublished().then(
        (count) => {
          unpublished.set(count);
        },
        () => undefined,
      );
    },
  });
  return unpublished;
}

@Injectable()
export class OutboxMetrics {
  private readonly published: Counter;
  private readonly failures: Counter;

  constructor(metrics: MetricsRegistry, @Inject(OUTBOX_STORE) store: OutboxStore) {
    const registers = [metrics.registry];
    this.published = new Counter({
      name: OUTBOX_METRIC_NAMES.published,
      help: 'Change events published from the outbox',
      registers,
    });
    this.failures = new Counter({
      name: OUTBOX_METRIC_NAMES.failures,
      help: 'Change events whose publication failed and will be retried',
      registers,
    });
    registerUnpublishedGauge(metrics.registry, store);
  }

  recordPublished(count: number): void {
    this.published.inc(count);
  }

  recordFailure(count: number): void {
    this.failures.inc(count);
  }
}
