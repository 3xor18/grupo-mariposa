import pino, { DestinationStream, Logger } from 'pino';
import { LogLevel } from '../app-config';
import { LOG_MESSAGE_KEY, SERVICE_NAME } from '../../shared/constants/logging.constants';
import { isoTimestamp, levelLabel } from '../../shared/observability/logger.params';

export const BOOTSTRAP_CONTEXT = 'ConfigServer';

export type BootstrapLogger = Pick<Logger, 'info' | 'warn'>;

export function createBootstrapLogger(level: LogLevel, destination?: DestinationStream): Logger {
  return pino(
    {
      level,
      messageKey: LOG_MESSAGE_KEY,
      base: { service: SERVICE_NAME, context: BOOTSTRAP_CONTEXT },
      timestamp: isoTimestamp,
      formatters: { level: levelLabel },
    },
    destination,
  );
}
