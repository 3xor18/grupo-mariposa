import { Global, Injectable, Module } from '@nestjs/common';
import { DATABASE_HEALTH, DatabaseHealth } from '../mongo/mongo.tokens';

@Injectable()
export class InProcessStorageHealth implements DatabaseHealth {
  isHealthy(): Promise<boolean> {
    return Promise.resolve(true);
  }
}

@Global()
@Module({
  providers: [{ provide: DATABASE_HEALTH, useClass: InProcessStorageHealth }],
  exports: [DATABASE_HEALTH],
})
export class MemoryStorageModule {}
