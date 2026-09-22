import { FaultType } from '../shared/fault-injection/fault-rule';
import { LogLevel } from './app-config';
import { CONFIG_DEFAULTS, InvalidConfigurationError, loadConfig } from './load-config';

const AUTH_ENV = {
  AUTH_ISSUER: 'http://localhost:8180/realms/mariposa',
  AUTH_JWKS_URL: 'http://keycloak:8080/realms/mariposa/protocol/openid-connect/certs',
};

describe('loadConfig', () => {
  it('should_apply_platform_defaults_when_only_auth_urls_are_set', () => {
    const config = loadConfig(AUTH_ENV);

    expect(config).toEqual({
      port: CONFIG_DEFAULTS.port,
      logLevel: LogLevel.INFO,
      auth: {
        enabled: true,
        issuer: AUTH_ENV.AUTH_ISSUER,
        jwksUrl: AUTH_ENV.AUTH_JWKS_URL,
        requiredRole: 'clients-reader',
      },
      faultInjection: { rules: [], timeoutMs: 5000 },
      rateLimit: { requestsPerSecond: 200, burst: 400 },
    });
  });

  it('should_parse_every_variable_when_all_are_provided', () => {
    const config = loadConfig({
      ...AUTH_ENV,
      PORT: '8082',
      LOG_LEVEL: 'debug',
      AUTH_REQUIRED_ROLE: 'custom-role',
      FAULT_RULES: 'CLI-40001:503:2, CLI-40002:timeout',
      FAULT_TIMEOUT_MS: '250',
      RATE_LIMIT_RPS: '10',
      RATE_LIMIT_BURST: '20',
    });

    expect(config.port).toBe(8082);
    expect(config.logLevel).toBe(LogLevel.DEBUG);
    expect(config.auth).toMatchObject({ requiredRole: 'custom-role' });
    expect(config.faultInjection).toEqual({
      rules: [
        { id: 'CLI-40001', type: FaultType.SERVICE_UNAVAILABLE, times: 2 },
        { id: 'CLI-40002', type: FaultType.TIMEOUT, times: Number.POSITIVE_INFINITY },
      ],
      timeoutMs: 250,
    });
    expect(config.rateLimit).toEqual({ requestsPerSecond: 10, burst: 20 });
  });

  it('should_disable_auth_without_urls_when_auth_enabled_is_false', () => {
    expect(loadConfig({ AUTH_ENABLED: 'false' }).auth).toEqual({ enabled: false });
  });

  it('should_fail_fast_when_auth_is_enabled_without_issuer_or_jwks', () => {
    expect(() => loadConfig({})).toThrow(
      /AUTH_ISSUER: is required when AUTH_ENABLED=true; AUTH_JWKS_URL: is required/,
    );
  });

  it.each([
    [{ PORT: 'abc' }, /PORT/],
    [{ PORT: '70000' }, /PORT/],
    [{ LOG_LEVEL: 'verbose' }, /LOG_LEVEL/],
    [{ AUTH_ENABLED: 'yes' }, /AUTH_ENABLED/],
    [{ AUTH_ISSUER: 'not a url' }, /AUTH_ISSUER/],
    [{ FAULT_RULES: 'CLI-1:418' }, /FAULT_RULES: .*Invalid fault rule "CLI-1:418"/],
    [{ FAULT_TIMEOUT_MS: '-1' }, /FAULT_TIMEOUT_MS/],
    [{ RATE_LIMIT_RPS: '0' }, /RATE_LIMIT_RPS/],
    [{ RATE_LIMIT_BURST: '1.5' }, /RATE_LIMIT_BURST/],
  ])('should_reject_invalid_environment_%j', (overrides, message) => {
    const load = (): unknown => loadConfig({ ...AUTH_ENV, ...overrides });

    expect(load).toThrow(InvalidConfigurationError);
    expect(load).toThrow(message);
  });
});
