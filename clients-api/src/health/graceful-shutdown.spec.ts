import { PinoLogger } from 'nestjs-pino';
import { testConfig } from '../../test/support/test-app';
import { PendingHolds } from '../shared/fault-injection/pending-holds';
import { GracefulShutdown, SHUTDOWN_MESSAGES } from './graceful-shutdown';
import { ReadinessState } from './readiness.state';
import { defaultShutdownHooks, FORCED_EXIT_CODE } from './shutdown-hooks';

describe('GracefulShutdown', () => {
  const config = testConfig('http://unused', { shutdown: { drainMs: 250, timeoutMs: 1000 } });
  const logger = { info: jest.fn(), error: jest.fn() };

  function setup(): {
    shutdown: GracefulShutdown;
    readiness: ReadinessState;
    holds: PendingHolds;
    hooks: { sleep: jest.Mock; forceExit: jest.Mock };
  } {
    const readiness = new ReadinessState();
    readiness.onApplicationBootstrap();
    const holds = new PendingHolds();
    const hooks = { sleep: jest.fn(() => Promise.resolve()), forceExit: jest.fn() };
    const shutdown = new GracefulShutdown(
      readiness,
      config,
      holds,
      hooks,
      logger as unknown as PinoLogger,
    );
    return { shutdown, readiness, holds, hooks };
  }

  beforeEach(() => {
    jest.useFakeTimers();
    jest.clearAllMocks();
  });

  afterEach(() => {
    jest.useRealTimers();
  });

  it('should_mark_not_ready_cancel_holds_and_wait_for_drain', async () => {
    const { shutdown, readiness, holds, hooks } = setup();

    await shutdown.beforeApplicationShutdown();

    expect(readiness.isReady()).toBe(false);
    expect(holds.cancelled).toBe(true);
    expect(hooks.sleep).toHaveBeenCalledWith(250);
    expect(logger.info).toHaveBeenCalledWith(
      { signal: undefined, drainMs: 250 },
      SHUTDOWN_MESSAGES.draining,
    );
    expect(jest.getTimerCount()).toBe(0);
  });

  it('should_force_exit_when_signal_shutdown_exceeds_drain_plus_timeout', async () => {
    const { shutdown, hooks } = setup();

    await shutdown.beforeApplicationShutdown('SIGTERM');
    jest.advanceTimersByTime(1249);
    expect(hooks.forceExit).not.toHaveBeenCalled();
    jest.advanceTimersByTime(1);

    expect(hooks.forceExit).toHaveBeenCalledTimes(1);
    expect(logger.error).toHaveBeenCalledWith({ timeoutMs: 1250 }, SHUTDOWN_MESSAGES.forced);
  });
});

describe('defaultShutdownHooks', () => {
  it('should_sleep_for_the_requested_time', async () => {
    const startedAt = Date.now();

    await defaultShutdownHooks.sleep(20);

    expect(Date.now() - startedAt).toBeGreaterThanOrEqual(19);
  });

  it('should_exit_the_process_with_failure_code', () => {
    const exit = jest.spyOn(process, 'exit').mockImplementation(() => undefined as never);

    defaultShutdownHooks.forceExit();

    expect(exit).toHaveBeenCalledWith(FORCED_EXIT_CODE);
    exit.mockRestore();
  });
});
