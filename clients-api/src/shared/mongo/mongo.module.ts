import {
  DynamicModule,
  Global,
  Inject,
  Injectable,
  Module,
  OnApplicationShutdown,
} from '@nestjs/common';
import { Db, MongoClient } from 'mongodb';
import { MongoStorageConfig } from '../../config/app-config';
import { SERVICE_NAME } from '../constants/logging.constants';
import {
  DATABASE_HEALTH,
  DatabaseHealth,
  MONGO_CLIENT,
  MONGO_DATABASE,
  MONGO_SETTINGS,
} from './mongo.tokens';

export const MONGO_TIMEOUTS = Object.freeze({ serverSelectionMs: 10_000, pingMs: 2000 });
const PING_COMMAND = Object.freeze({ ping: 1 });

export async function connectMongo(settings: MongoStorageConfig): Promise<MongoClient> {
  const client = new MongoClient(settings.uri, {
    appName: SERVICE_NAME,
    serverSelectionTimeoutMS: MONGO_TIMEOUTS.serverSelectionMs,
  });
  await client.connect();
  return client;
}

export function databaseOf(client: MongoClient, settings: MongoStorageConfig): Db {
  return client.db(settings.database);
}

@Injectable()
export class MongoLifecycle implements OnApplicationShutdown {
  constructor(@Inject(MONGO_CLIENT) private readonly client: MongoClient) {}

  async onApplicationShutdown(): Promise<void> {
    await this.client.close();
  }
}

@Injectable()
export class MongoHealth implements DatabaseHealth {
  constructor(@Inject(MONGO_DATABASE) private readonly database: Db) {}

  async isHealthy(): Promise<boolean> {
    try {
      await this.database.command(PING_COMMAND, { timeoutMS: MONGO_TIMEOUTS.pingMs });
      return true;
    } catch {
      return false;
    }
  }
}

@Global()
@Module({})
export class MongoModule {
  static forRoot(settings: MongoStorageConfig): DynamicModule {
    return {
      module: MongoModule,
      providers: [
        { provide: MONGO_SETTINGS, useValue: settings },
        { provide: MONGO_CLIENT, useFactory: connectMongo, inject: [MONGO_SETTINGS] },
        { provide: MONGO_DATABASE, useFactory: databaseOf, inject: [MONGO_CLIENT, MONGO_SETTINGS] },
        { provide: DATABASE_HEALTH, useClass: MongoHealth },
        MongoLifecycle,
      ],
      exports: [MONGO_CLIENT, MONGO_DATABASE, DATABASE_HEALTH],
    };
  }
}
