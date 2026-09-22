import { setTimeout as delay } from 'node:timers/promises';

export const SHUTDOWN_HOOKS = Symbol('SHUTDOWN_HOOKS');
export const FORCED_EXIT_CODE = 1;

export interface ShutdownHooks {
  readonly sleep: (milliseconds: number) => Promise<void>;
  readonly forceExit: () => void;
}

export const defaultShutdownHooks: ShutdownHooks = Object.freeze({
  sleep: async (milliseconds: number): Promise<void> => {
    await delay(milliseconds);
  },
  forceExit: (): void => {
    process.exit(FORCED_EXIT_CODE);
  },
});
