import { Writable } from 'node:stream';
import { ConfigServerStub } from '../../../test/support/config-server';
import { LogLevel } from '../app-config';
import { loadConfig } from '../load-config';
import { createBootstrapLogger } from './bootstrap-logger';
import { ConfigServerUnavailableError } from './config-server-client';
import { loadRemoteConfig, REMOTE_CONFIG_MESSAGES } from './load-remote-config';

const REMOTE_PROPERTIES = [
  'auth.issuer: http://localhost:8180/realms/mariposa',
  'auth.jwks-url: http://keycloak:8080/realms/mariposa/protocol/openid-connect/certs',
  'rate-limit.rps: 50',
  'rate-limit.burst: 60',
  'fault.rules: CLI-40001:503:2',
  'client.secret: do-not-log',
  'management.endpoints.web.exposure.include: health',
].join('\n');

describe('loadRemoteConfig', () => {
  let stub: ConfigServerStub;
  const logger = { info: jest.fn(), warn: jest.fn() };
  const sleep = jest.fn(() => Promise.resolve());
  const fetchSpy = jest.fn((input: string | URL | Request, init?: RequestInit) =>
    fetch(input, init),
  );
  const dependencies = { fetch: fetchSpy, sleep, logger };

  const environment = (overrides: Record<string, string> = {}): Record<string, string> => ({
    CONFIG_SERVER_URL: stub.url,
    CONFIG_PROFILE: 'docker',
    CONFIG_SERVER_USERNAME: 'config',
    CONFIG_SERVER_PASSWORD: 'super-secret-password',
    CONFIG_SERVER_RETRIES: '1',
    ...overrides,
  });

  beforeAll(async () => {
    stub = await ConfigServerStub.start();
  });

  afterAll(async () => {
    await stub.stop();
  });

  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('should_return_environment_untouched_when_config_server_url_is_empty', async () => {
    const source = { CONFIG_SERVER_URL: '', PORT: '1234' };

    await expect(loadRemoteConfig(source, dependencies)).resolves.toBe(source);
    expect(fetchSpy).not.toHaveBeenCalled();
  });

  it('should_merge_with_precedence_environment_then_remote_then_defaults', async () => {
    stub.respond({ status: 200, body: REMOTE_PROPERTIES });

    const source = await loadRemoteConfig(
      environment({ RATE_LIMIT_BURST: '99', FAULT_RULES: '' }),
      dependencies,
    );
    const config = loadConfig(source);

    expect(config.rateLimit).toEqual({ requestsPerSecond: 50, burst: 99 });
    expect(config.faultInjection.rules).toEqual([{ id: 'CLI-40001', type: '503', times: 2 }]);
    expect(config.faultInjection.timeoutMs).toBe(5000);
    expect(config.auth).toMatchObject({
      enabled: true,
      jwksUrl: 'http://keycloak:8080/realms/mariposa/protocol/openid-connect/certs',
    });
    expect(source.MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE).toBe('health');
  });

  it('should_log_loaded_properties_without_secrets_or_credentials', async () => {
    stub.respond({ status: 200, body: REMOTE_PROPERTIES });

    await loadRemoteConfig(environment(), dependencies);

    const logged = JSON.stringify(logger.info.mock.calls);
    expect(logger.info).toHaveBeenCalledWith(
      expect.objectContaining({ url: `${stub.url}/clients-api-docker.properties` }),
      REMOTE_CONFIG_MESSAGES.loaded,
    );
    expect(logged).toContain('CLIENT_SECRET');
    expect(logged).not.toContain('do-not-log');
    expect(logged).not.toContain('super-secret-password');
  });

  it('should_throw_when_unreachable_and_fail_fast_is_enabled', async () => {
    stub.respond({ status: 503 }, { status: 503 });

    await expect(
      loadRemoteConfig(environment({ CONFIG_SERVER_FAIL_FAST: 'true' }), dependencies),
    ).rejects.toBeInstanceOf(ConfigServerUnavailableError);
    expect(sleep).toHaveBeenCalledTimes(1);
  });

  it('should_warn_and_continue_with_environment_when_fail_fast_is_disabled', async () => {
    stub.respond({ status: 401 });
    const source = environment({ PORT: '4000' });

    const merged = await loadRemoteConfig(source, dependencies);

    expect(merged).toEqual(source);
    expect(logger.warn).toHaveBeenCalledWith(
      { reason: expect.stringContaining('unexpected status 401') as string },
      REMOTE_CONFIG_MESSAGES.unavailable,
    );
    expect(JSON.stringify(logger.warn.mock.calls)).not.toContain('super-secret-password');
  });

  it('should_use_real_transport_and_logger_by_default', async () => {
    stub.respond({ status: 500 }, { status: 200, body: 'rate-limit.rps: 7' });

    const source = await loadRemoteConfig(environment({ LOG_LEVEL: LogLevel.SILENT }));

    expect(source.RATE_LIMIT_RPS).toBe('7');
    expect(stub.requests).toHaveLength(2);
  });
});

describe('createBootstrapLogger', () => {
  it('should_write_structured_json_lines_with_service_and_context', () => {
    const lines: string[] = [];
    const destination = new Writable({
      write(chunk: Buffer, _encoding, callback) {
        lines.push(chunk.toString());
        callback();
      },
    });

    createBootstrapLogger(LogLevel.INFO, destination).warn({ reason: 'x' }, 'hello');

    expect(JSON.parse(lines[0] ?? '{}')).toMatchObject({
      level: 'warn',
      service: 'clients-api',
      context: 'ConfigServer',
      message: 'hello',
      reason: 'x',
      timestamp: expect.any(String) as string,
    });
  });
});
