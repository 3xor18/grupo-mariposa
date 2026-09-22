import { LOG_MESSAGE_KEY, SERVICE_NAME } from '../shared/constants/logging.constants';

export const FATAL_EXIT_CODE = 1;
export const FATAL_LEVEL = 'fatal';
export const FATAL_EVENTS = Object.freeze(['unhandledRejection', 'uncaughtException'] as const);
const LINE_BREAK = '\n';

export interface FatalSink {
  readonly write: (line: string) => void;
  readonly exit: (code: number) => void;
}

export type FatalEventSource = Pick<NodeJS.Process, 'once'>;

export const processFatalSink: FatalSink = Object.freeze({
  write: (line: string): void => {
    process.stderr.write(line);
  },
  exit: (code: number): void => {
    process.exit(code);
  },
});

export function fatalLine(error: unknown, now: Date = new Date()): string {
  const entry = {
    level: FATAL_LEVEL,
    timestamp: now.toISOString(),
    service: SERVICE_NAME,
    [LOG_MESSAGE_KEY]: String(error),
    ...(error instanceof Error && error.stack !== undefined ? { stack: error.stack } : {}),
  };
  return `${JSON.stringify(entry)}${LINE_BREAK}`;
}

export function exitFatally(error: unknown, sink: FatalSink = processFatalSink): void {
  sink.write(fatalLine(error));
  sink.exit(FATAL_EXIT_CODE);
}

export function registerFatalHandlers(
  source: FatalEventSource,
  onFatal: (error: unknown) => void,
): void {
  for (const event of FATAL_EVENTS) {
    source.once(event, onFatal);
  }
}
