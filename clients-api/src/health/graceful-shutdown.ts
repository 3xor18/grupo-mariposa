import { BeforeApplicationShutdown, Inject, Injectable } from '@nestjs/common';
import { InjectPinoLogger, PinoLogger } from 'nestjs-pino';
import { APP_CONFIG, AppConfig } from '../config/app-config';
import { PENDING_HOLDS } from '../shared/fault-injection/fault-injection.tokens';
import { PendingHolds } from '../shared/fault-injection/pending-holds';
import { ReadinessState } from './readiness.state';
import { SHUTDOWN_HOOKS, ShutdownHooks } from './shutdown-hooks';

export const SHUTDOWN_MESSAGES = Object.freeze({
  draining: 'Shutdown started, draining in-flight requests',
  forced: 'Shutdown did not finish in time, forcing exit',
});

@Injectable()
export class GracefulShutdown implements BeforeApplicationShutdown {
  constructor(
    private readonly readiness: ReadinessState,
    @Inject(APP_CONFIG) private readonly config: AppConfig,
    @Inject(PENDING_HOLDS) private readonly holds: PendingHolds,
    @Inject(SHUTDOWN_HOOKS) private readonly hooks: ShutdownHooks,
    @InjectPinoLogger(GracefulShutdown.name) private readonly logger: PinoLogger,
  ) {}

  async beforeApplicationShutdown(signal?: string): Promise<void> {
    const { drainMs, timeoutMs } = this.config.shutdown;
    this.logger.info({ signal, drainMs }, SHUTDOWN_MESSAGES.draining);
    this.readiness.markDraining();
    this.holds.cancelAll();
    if (signal !== undefined) {
      this.armHardTimeout(drainMs + timeoutMs);
    }
    await this.hooks.sleep(drainMs);
  }

  private armHardTimeout(milliseconds: number): void {
    const timer = setTimeout(() => {
      this.logger.error({ timeoutMs: milliseconds }, SHUTDOWN_MESSAGES.forced);
      this.hooks.forceExit();
    }, milliseconds);
    timer.unref();
  }
}
