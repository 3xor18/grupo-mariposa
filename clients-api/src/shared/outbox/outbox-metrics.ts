import { Inject, Injectable } from '@nestjs/common';
import { Counter, Gauge, Registry } from 'prom-client';
import { MetricsRegistry } from '../observability/metrics-registry';
import { CLOCK, Clock } from '../time/clock';
import { OUTBOX_STORE, OutboxStore } from './outbox.store';

export const OUTBOX_METRIC_NAMES = Object.freeze({
  published: 'outbox_published_total',
  failures: 'outbox_publish_failures_total',
  pending: 'outbox_pending',
  oldestAge: 'outbox_oldest_age_seconds',
});

const MILLISECONDS_PER_SECOND = 1000;
const EMPTY_BACKLOG_AGE = 0;

function refreshed<T>(read: () => Promise<T>, apply: (value: T) => void): () => Promise<void> {
  return async (): Promise<void> => {
    await read().then(apply, () => undefined);
  };
}

export function ageInSeconds(createdAt: Date | undefined, now: Date): number {
  return createdAt === undefined
    ? EMPTY_BACKLOG_AGE
    : (now.getTime() - createdAt.getTime()) / MILLISECONDS_PER_SECOND;
}

export function registerBacklogGauges(registry: Registry, store: OutboxStore, clock: Clock): void {
  const pending: Gauge = new Gauge({
    name: OUTBOX_METRIC_NAMES.pending,
    help: 'Outbox entries not yet published (pending or in flight)',
    registers: [registry],
    collect: refreshed(
      () => store.countPending(),
      (count) => {
        pending.set(count);
      },
    ),
  });
  const oldestAge: Gauge = new Gauge({
    name: OUTBOX_METRIC_NAMES.oldestAge,
    help: 'Age in seconds of the oldest unpublished outbox entry',
    registers: [registry],
    collect: refreshed(
      () => store.oldestPendingCreatedAt(),
      (createdAt) => {
        oldestAge.set(ageInSeconds(createdAt, clock()));
      },
    ),
  });
}

@Injectable()
export class OutboxMetrics {
  private readonly published: Counter;
  private readonly failures: Counter;

  constructor(
    metrics: MetricsRegistry,
    @Inject(OUTBOX_STORE) store: OutboxStore,
    @Inject(CLOCK) clock: Clock,
  ) {
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
    registerBacklogGauges(metrics.registry, store, clock);
  }

  recordPublished(count: number): void {
    this.published.inc(count);
  }

  recordFailure(count: number): void {
    this.failures.inc(count);
  }
}
