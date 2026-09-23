import { FaultType } from '../shared/fault-injection/fault-rule';
import { LogLevel } from './app-config';
import { InvalidConfigurationError, loadConfig } from './load-config';

const AUTH_ENV = {
  AUTH_ISSUER: 'http://localhost:8180/realms/mariposa',
  AUTH_JWKS_URL: 'http://keycloak:8080/realms/mariposa/protocol/openid-connect/certs',
};

describe('loadConfig', () => {
  it('should_apply_platform_defaults_when_only_auth_urls_are_set', () => {
    expect(loadConfig(AUTH_ENV)).toEqual({
      port: 3000,
      logLevel: LogLevel.INFO,
      auth: {
        enabled: true,
        issuer: AUTH_ENV.AUTH_ISSUER,
        jwksUrl: AUTH_ENV.AUTH_JWKS_URL,
        requiredRole: 'clients-reader',
        audience: 'clients-api',
      },
      faultInjection: { enabled: false, rules: [], timeoutMs: 5000 },
      rateLimit: { requestsPerSecond: 200, burst: 400, maxTrackedCallers: 10_000 },
      shutdown: { drainMs: 5000, timeoutMs: 10_000 },
      http: { apiDocsEnabled: false, trustProxy: false },
    });
  });

  it('should_parse_every_variable_when_all_are_provided', () => {
    const config = loadConfig({
      ...AUTH_ENV,
      PORT: '8082',
      LOG_LEVEL: 'debug',
      AUTH_REQUIRED_ROLE: 'custom-role',
      AUTH_AUDIENCE: 'clients-api-v2',
      API_DOCS_ENABLED: 'true',
      TRUST_PROXY: '1',
      FAULT_INJECTION_ENABLED: 'true',
      FAULT_RULES: 'CLI-40001:503:2, CLI-40002:timeout',
      FAULT_TIMEOUT_MS: '250',
      RATE_LIMIT_RPS: '10',
      RATE_LIMIT_BURST: '20',
      RATE_LIMIT_MAX_TRACKED_CALLERS: '50',
      SHUTDOWN_DRAIN_MS: '0',
      SHUTDOWN_TIMEOUT_MS: '1500',
      MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE: 'health',
    });

    expect(config).toEqual({
      port: 8082,
      logLevel: LogLevel.DEBUG,
      auth: {
        enabled: true,
        issuer: AUTH_ENV.AUTH_ISSUER,
        jwksUrl: AUTH_ENV.AUTH_JWKS_URL,
        requiredRole: 'custom-role',
        audience: 'clients-api-v2',
      },
      faultInjection: {
        enabled: true,
        rules: [
          { id: 'CLI-40001', type: FaultType.SERVICE_UNAVAILABLE, times: 2 },
          { id: 'CLI-40002', type: FaultType.TIMEOUT, times: Number.POSITIVE_INFINITY },
        ],
        timeoutMs: 250,
      },
      rateLimit: { requestsPerSecond: 10, burst: 20, maxTrackedCallers: 50 },
      shutdown: { drainMs: 0, timeoutMs: 1500 },
      http: { apiDocsEnabled: true, trustProxy: 1 },
    });
  });

  it('should_disable_auth_without_urls_when_auth_enabled_is_false', () => {
    expect(loadConfig({ AUTH_ENABLED: 'false' }).auth).toEqual({ enabled: false });
  });

  it('should_fail_closed_when_auth_is_enabled_without_issuer_or_jwks', () => {
    expect(() => loadConfig({})).toThrow(
      new InvalidConfigurationError([
        'AUTH_ISSUER: is required when AUTH_ENABLED=true',
        'AUTH_JWKS_URL: is required when AUTH_ENABLED=true',
      ]),
    );
    expect(() => loadConfig({ AUTH_ISSUER: AUTH_ENV.AUTH_ISSUER })).toThrow(
      'Invalid configuration: AUTH_JWKS_URL: is required when AUTH_ENABLED=true',
    );
  });

  it('should_refuse_unsafe_switches_in_production', () => {
    expect(() =>
      loadConfig({
        NODE_ENV: 'production',
        AUTH_ENABLED: 'false',
        FAULT_INJECTION_ENABLED: 'true',
        API_DOCS_ENABLED: 'true',
      }),
    ).toThrow(
      'Invalid configuration: AUTH_ENABLED=false is not allowed when NODE_ENV=production; ' +
        'FAULT_INJECTION_ENABLED=true is not allowed when NODE_ENV=production; ' +
        'API_DOCS_ENABLED=true is not allowed when NODE_ENV=production',
    );
  });

  it.each([
    [{ AUTH_ENABLED: 'false' }, 'AUTH_ENABLED=false'],
    [{ FAULT_INJECTION_ENABLED: 'true' }, 'FAULT_INJECTION_ENABLED=true'],
    [{ API_DOCS_ENABLED: 'true' }, 'API_DOCS_ENABLED=true'],
  ])('should_refuse_%j_in_production', (overrides, setting) => {
    expect(() => loadConfig({ ...AUTH_ENV, NODE_ENV: 'production', ...overrides })).toThrow(
      `Invalid configuration: ${setting} is not allowed when NODE_ENV=production`,
    );
  });

  it('should_start_in_production_with_secure_defaults', () => {
    const config = loadConfig({ ...AUTH_ENV, NODE_ENV: 'production' });

    expect(config.auth).toMatchObject({ enabled: true, audience: 'clients-api' });
    expect(config.faultInjection.enabled).toBe(false);
    expect(config.http.apiDocsEnabled).toBe(false);
  });

  it.each([
    ['true', true],
    ['false', false],
    ['2', 2],
    ['loopback, 10.0.0.0/8', 'loopback, 10.0.0.0/8'],
  ])('should_parse_trust_proxy_%s', (raw, expected) => {
    expect(loadConfig({ ...AUTH_ENV, TRUST_PROXY: raw }).http.trustProxy).toEqual(expected);
  });

  it.each([
    [{ PORT: 'abc' }, 'PORT: Invalid input: expected number, received NaN'],
    [{ PORT: '70000' }, 'PORT: Too big: expected number to be <=65535'],
    [{ LOG_LEVEL: 'verbose' }, 'LOG_LEVEL: Invalid option'],
    [{ AUTH_ENABLED: 'yes' }, 'AUTH_ENABLED: Invalid option: expected one of "true"|"false"'],
    [{ AUTH_ISSUER: 'not a url' }, 'AUTH_ISSUER: Invalid URL'],
    [{ AUTH_AUDIENCE: '' }, 'AUTH_AUDIENCE: Too small'],
    [{ AUTH_AUDIENCE: '   ' }, 'AUTH_AUDIENCE: Too small'],
    [{ TRUST_PROXY: ' ' }, 'TRUST_PROXY: Too small'],
    [{ API_DOCS_ENABLED: 'yes' }, 'API_DOCS_ENABLED: Invalid option'],
    [
      { FAULT_RULES: 'CLI-1:418' },
      'FAULT_RULES: FaultRuleSyntaxError: Invalid fault rule "CLI-1:418"',
    ],
    [{ FAULT_TIMEOUT_MS: '-1' }, 'FAULT_TIMEOUT_MS: Too small'],
    [{ RATE_LIMIT_RPS: '0' }, 'RATE_LIMIT_RPS: Too small'],
    [{ RATE_LIMIT_BURST: '1.5' }, 'RATE_LIMIT_BURST: Invalid input: expected int'],
    [{ RATE_LIMIT_MAX_TRACKED_CALLERS: '0' }, 'RATE_LIMIT_MAX_TRACKED_CALLERS: Too small'],
    [{ SHUTDOWN_DRAIN_MS: '-5' }, 'SHUTDOWN_DRAIN_MS: Too small'],
  ])('should_reject_invalid_environment_%j', (overrides, message) => {
    const load = (): unknown => loadConfig({ ...AUTH_ENV, ...overrides });

    expect(load).toThrow(InvalidConfigurationError);
    expect(load).toThrow(message);
  });
});
