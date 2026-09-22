import pino, { DestinationStream, Logger } from 'pino';
import { LogLevel } from '../app-config';
import { isoTimestamp, levelLabel, SERVICE_NAME } from '../../shared/observability/logger.params';

export const BOOTSTRAP_CONTEXT = 'ConfigServer';
const MESSAGE_KEY = 'message';

export type BootstrapLogger = Pick<Logger, 'info' | 'warn'>;

export function createBootstrapLogger(level: LogLevel, destination?: DestinationStream): Logger {
  return pino(
    {
      level,
      messageKey: MESSAGE_KEY,
      base: { service: SERVICE_NAME, context: BOOTSTRAP_CONTEXT },
      timestamp: isoTimestamp,
      formatters: { level: levelLabel },
    },
    destination,
  );
}
