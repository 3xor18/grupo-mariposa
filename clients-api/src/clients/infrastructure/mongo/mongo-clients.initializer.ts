import { Inject, Injectable, OnModuleInit } from '@nestjs/common';
import { AnyBulkWriteOperation, Db } from 'mongodb';
import { InjectPinoLogger, PinoLogger } from 'nestjs-pino';
import { APP_CONFIG, AppConfig } from '../../../config/app-config';
import { MARKET_CATALOG, MarketCatalog } from '../../../shared/markets/market-catalog';
import { MONGO_DATABASE } from '../../../shared/mongo/mongo.tokens';
import { CLOCK, Clock } from '../../../shared/time/clock';
import { Client } from '../../domain/client';
import { CLIENT_SEED, seedForCatalog } from '../client.seed';
import { CLIENTS_COLLECTION, ClientDocument, toClientDocument } from './client.document';

export const SEED_MESSAGE = 'Client seed applied';
export const SEED_DISABLED_MESSAGE = 'Client seed disabled';
const UNIQUE_CLIENT_ID = Object.freeze({ clientId: 1 } as const);

export function seedOperations(
  clients: readonly Client[],
  now: Date,
): AnyBulkWriteOperation<ClientDocument>[] {
  return clients.map((client) => ({
    updateOne: {
      filter: { clientId: client.id },
      update: { $setOnInsert: toClientDocument(client, now) },
      upsert: true,
    },
  }));
}

@Injectable()
export class MongoClientsInitializer implements OnModuleInit {
  constructor(
    @Inject(MONGO_DATABASE) private readonly database: Db,
    @Inject(APP_CONFIG) private readonly config: AppConfig,
    @Inject(MARKET_CATALOG) private readonly catalog: MarketCatalog,
    @Inject(CLOCK) private readonly clock: Clock,
    @InjectPinoLogger(MongoClientsInitializer.name) private readonly logger: PinoLogger,
  ) {}

  async onModuleInit(): Promise<void> {
    const clients = this.database.collection<ClientDocument>(CLIENTS_COLLECTION);
    await clients.createIndex(UNIQUE_CLIENT_ID, { unique: true });
    if (!this.config.seedEnabled) {
      this.logger.info(SEED_DISABLED_MESSAGE);
      return;
    }
    const seed = seedForCatalog(this.catalog);
    const result = await clients.bulkWrite(seedOperations(seed, this.clock()), { ordered: false });
    this.logger.info(
      { inserted: result.upsertedCount, skipped: CLIENT_SEED.length - seed.length },
      SEED_MESSAGE,
    );
  }
}
