import { Global, Inject, Injectable, Module, OnModuleInit } from '@nestjs/common';
import { Db } from 'mongodb';
import { APP_CONFIG, AppConfig } from '../../config/app-config';
import { MONGO_DATABASE } from '../mongo/mongo.tokens';
import { CLOCK, Clock } from '../time/clock';
import {
  CHANGE_EVENT_PUBLISHER,
  ChangeEventPublisher,
  createChangeEventPublisher,
} from './change-event-publisher';
import { OutboxMetrics } from './outbox-metrics';
import { OutboxRelay } from './outbox-relay';
import { MongoOutboxStore, OUTBOX_STORE } from './outbox.store';

export function publisherFor(config: AppConfig): ChangeEventPublisher {
  return createChangeEventPublisher(config.kafka);
}

export function createOutboxStore(database: Db, clock: Clock): MongoOutboxStore {
  return new MongoOutboxStore(database, clock);
}

@Injectable()
export class OutboxIndexes implements OnModuleInit {
  constructor(@Inject(OUTBOX_STORE) private readonly store: MongoOutboxStore) {}

  async onModuleInit(): Promise<void> {
    await this.store.ensureIndexes();
  }
}

@Global()
@Module({
  providers: [
    { provide: OUTBOX_STORE, useFactory: createOutboxStore, inject: [MONGO_DATABASE, CLOCK] },
    {
      provide: CHANGE_EVENT_PUBLISHER,
      useFactory: publisherFor,
      inject: [APP_CONFIG],
    },
    OutboxIndexes,
    OutboxMetrics,
    OutboxRelay,
  ],
  exports: [OUTBOX_STORE, OutboxRelay],
})
export class OutboxModule {}
