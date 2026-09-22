import { IncomingMessage } from 'node:http';
import { Params } from 'nestjs-pino';
import { AppConfig } from '../../config/app-config';
import { TraceContextStore } from './trace-context';

export const SERVICE_NAME = 'clients-api';
export const REDACTED_PATHS = [
  'req.headers.authorization',
  'req.headers.cookie',
  'res.headers["set-cookie"]',
];
export const QUIET_PATH_PREFIXES = ['/health', '/metrics'];

const REDACTION_CENSOR = '[REDACTED]';
const MESSAGE_KEY = 'message';

export function isQuietPath(request: IncomingMessage): boolean {
  const url = request.url ?? '';
  return QUIET_PATH_PREFIXES.some((prefix) => url.startsWith(prefix));
}

export function isoTimestamp(): string {
  return `,"timestamp":"${new Date().toISOString()}"`;
}

export function levelLabel(label: string): Record<string, string> {
  return { level: label };
}

export function buildLoggerParams(config: AppConfig, traceContext: TraceContextStore): Params {
  return {
    pinoHttp: {
      level: config.logLevel,
      messageKey: MESSAGE_KEY,
      base: { service: SERVICE_NAME },
      timestamp: isoTimestamp,
      formatters: { level: levelLabel },
      redact: { paths: REDACTED_PATHS, censor: REDACTION_CENSOR },
      genReqId: () => traceContext.currentTraceId(),
      customProps: () => ({ ...traceContext.current() }),
      autoLogging: { ignore: isQuietPath },
    },
  };
}
