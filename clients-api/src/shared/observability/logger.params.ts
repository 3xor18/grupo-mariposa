import { IncomingMessage } from 'node:http';
import { Params } from 'nestjs-pino';
import { AppConfig } from '../../config/app-config';
import { LOG_MESSAGE_KEY, REDACTION_CENSOR, SERVICE_NAME } from '../constants/logging.constants';
import { absolutePath, ROUTES } from '../constants/routes.constants';
import { TraceContextStore } from './trace-context';

export const REDACTED_PATHS: readonly string[] = Object.freeze([
  'req.headers.authorization',
  'req.headers["proxy-authorization"]',
  'req.headers["x-api-key"]',
  'req.headers.cookie',
  'res.headers["set-cookie"]',
]);

export const QUIET_PATHS: ReadonlySet<string> = new Set([
  absolutePath(ROUTES.HEALTH, ROUTES.LIVENESS),
  absolutePath(ROUTES.HEALTH, ROUTES.READINESS),
  absolutePath(ROUTES.METRICS),
]);

const QUERY_SEPARATOR = '?';

function pathOf(url: string): string {
  const queryStart = url.indexOf(QUERY_SEPARATOR);
  return queryStart === -1 ? url : url.slice(0, queryStart);
}

export function isQuietPath(request: IncomingMessage): boolean {
  return QUIET_PATHS.has(pathOf(request.url ?? ''));
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
      messageKey: LOG_MESSAGE_KEY,
      base: { service: SERVICE_NAME },
      timestamp: isoTimestamp,
      formatters: { level: levelLabel },
      redact: { paths: [...REDACTED_PATHS], censor: REDACTION_CENSOR },
      genReqId: () => traceContext.currentTraceId(),
      customProps: () => ({ ...traceContext.current() }),
      autoLogging: { ignore: isQuietPath },
    },
  };
}
