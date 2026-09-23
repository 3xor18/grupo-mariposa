import {
  BeforeApplicationShutdown,
  Inject,
  Injectable,
  OnApplicationBootstrap,
} from '@nestjs/common';
import { hostname } from 'node:os';
import { InjectPinoLogger, PinoLogger } from 'nestjs-pino';
import { APP_CONFIG, AppConfig } from '../../config/app-config';
import { ID_GENERATOR, IdGenerator } from '../ids/uuid-v7';
import { CHANGE_EVENT_PUBLISHER, ChangeEventPublisher } from './change-event-publisher';
import { OutboxMetrics } from './outbox-metrics';
import { OUTBOX_STORE, OutboxStore } from './outbox.store';

export const RELAY_MESSAGES = Object.freeze({
  disabled: 'Outbox relay disabled: Kafka is not configured',
  publishFailed: 'Publishing outbox batch failed, entries released for retry',
  leaseLost: 'Some outbox entries were re-leased by another relay before being marked',
  tickFailed: 'Outbox relay iteration failed',
});

const OWNER_SEPARATOR = ':';

@Injectable()
export class OutboxRelay implements OnApplicationBootstrap, BeforeApplicationShutdown {
  readonly owner: string;
  private timer: NodeJS.Timeout | undefined;
  private current: Promise<void> = Promise.resolve();
  private stopped = false;

  constructor(
    @Inject(APP_CONFIG) private readonly config: AppConfig,
    @Inject(OUTBOX_STORE) private readonly store: OutboxStore,
    @Inject(CHANGE_EVENT_PUBLISHER) private readonly publisher: ChangeEventPublisher,
    private readonly metrics: OutboxMetrics,
    @Inject(ID_GENERATOR) ids: IdGenerator,
    @InjectPinoLogger(OutboxRelay.name) private readonly logger: PinoLogger,
  ) {
    this.owner = [hostname(), String(process.pid), ids()].join(OWNER_SEPARATOR);
  }

  onApplicationBootstrap(): void {
    if (this.config.kafka.bootstrapServers.length === 0) {
      this.logger.warn(RELAY_MESSAGES.disabled);
      return;
    }
    this.schedule();
  }

  async beforeApplicationShutdown(): Promise<void> {
    this.stopped = true;
    clearTimeout(this.timer);
    await this.current;
    await this.publisher.close();
  }

  async relayOnce(): Promise<number> {
    const { batchSize, leaseMs } = this.config.outbox;
    const batch = await this.store.claim(this.owner, batchSize, leaseMs);
    if (batch.length === 0) {
      return 0;
    }
    const ids = batch.map((message) => message.id);
    try {
      await this.publisher.publish(batch);
    } catch (error: unknown) {
      this.metrics.recordFailure(batch.length);
      this.logger.warn({ err: error, count: batch.length }, RELAY_MESSAGES.publishFailed);
      await this.store.release(this.owner, ids, String(error));
      return 0;
    }
    return this.markPublished(ids);
  }

  private async markPublished(ids: readonly string[]): Promise<number> {
    const marked = await this.store.markPublished(this.owner, ids);
    if (marked < ids.length) {
      this.logger.warn({ expected: ids.length, marked }, RELAY_MESSAGES.leaseLost);
    }
    this.metrics.recordPublished(marked);
    return marked;
  }

  private schedule(): void {
    this.timer = setTimeout(() => {
      this.current = this.tick();
    }, this.config.outbox.relayIntervalMs);
  }

  private async tick(): Promise<void> {
    try {
      await this.relayOnce();
    } catch (error: unknown) {
      this.logger.error({ err: error }, RELAY_MESSAGES.tickFailed);
    }
    if (!this.stopped) {
      this.schedule();
    }
  }
}
