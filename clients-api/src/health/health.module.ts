import { Module } from '@nestjs/common';
import { GracefulShutdown } from './graceful-shutdown';
import { HealthController } from './health.controller';
import { ReadinessState } from './readiness.state';
import { defaultShutdownHooks, SHUTDOWN_HOOKS } from './shutdown-hooks';

@Module({
  controllers: [HealthController],
  providers: [
    ReadinessState,
    GracefulShutdown,
    { provide: SHUTDOWN_HOOKS, useValue: defaultShutdownHooks },
  ],
})
export class HealthModule {}
