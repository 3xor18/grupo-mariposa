import { IncomingMessage } from 'node:http';
import { AppConfig, LogLevel } from '../../config/app-config';
import {
  buildLoggerParams,
  isoTimestamp,
  isQuietPath,
  levelLabel,
  QUIET_PATHS,
  REDACTED_PATHS,
} from './logger.params';
import { SERVICE_NAME } from '../constants/logging.constants';
import { TraceContextStore } from './trace-context';

describe('logger params', () => {
  const config = { logLevel: LogLevel.WARN } as AppConfig;
  const store = new TraceContextStore();

  it('should_configure_json_logs_with_service_level_and_redaction', () => {
    const { pinoHttp } = buildLoggerParams(config, store) as {
      pinoHttp: Record<string, unknown>;
    };

    expect(pinoHttp).toMatchObject({
      level: LogLevel.WARN,
      messageKey: 'message',
      base: { service: SERVICE_NAME },
      redact: { paths: REDACTED_PATHS },
    });
    expect(REDACTED_PATHS).toEqual([
      'req.headers.authorization',
      'req.headers["proxy-authorization"]',
      'req.headers["x-api-key"]',
      'req.headers.cookie',
      'res.headers["set-cookie"]',
    ]);
    expect(Object.isFrozen(REDACTED_PATHS)).toBe(true);
    expect([...QUIET_PATHS]).toEqual(['/health/live', '/health/ready', '/metrics']);
  });

  it('should_bind_trace_context_to_request_logs', () => {
    const { pinoHttp } = buildLoggerParams(config, store) as {
      pinoHttp: { genReqId: () => string; customProps: () => object };
    };
    const context = { traceId: 'trace-1', requestId: 'req-1' };

    expect(store.run(context, () => pinoHttp.genReqId())).toBe('trace-1');
    expect(store.run(context, () => pinoHttp.customProps())).toEqual(context);
    expect(pinoHttp.customProps()).toEqual({});
  });

  it('should_render_iso_timestamp_and_level_label', () => {
    expect(isoTimestamp()).toMatch(/^,"timestamp":"\d{4}-\d{2}-\d{2}T[\d:.]+Z"$/);
    expect(levelLabel('info')).toEqual({ level: 'info' });
  });

  it.each([
    ['/health/live', true],
    ['/health/ready?probe=k8s', true],
    ['/metrics', true],
    ['/healthcheck', false],
    ['/health/live/extra', false],
    ['/metrics-export', false],
    ['/clients/CLI-1', false],
    [undefined, false],
  ])('should_silence_probe_logs_for_%s', (url, expected) => {
    expect(isQuietPath({ url } as IncomingMessage)).toBe(expected);
  });
});
