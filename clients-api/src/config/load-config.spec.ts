import { FaultType } from '../shared/fault-injection/fault-rule';
import { DEFAULT_PLATFORM_MARKETS, parseMarkets } from '../shared/markets/market-catalog';
import { LogLevel, StorageDriver } from './app-config';
import { InvalidConfigurationError, loadConfig } from './load-config';

const MONGO_URI = 'mongodb://mongo:27017/?replicaSet=rs0';
const BASE_ENV = { MONGODB_URI: MONGO_URI };
const AUTH_ENV = {
  ...BASE_ENV,
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
        adminRole: 'clients-admin',
        audience: 'clients-api',
      },
      faultInjection: { enabled: false, rules: [], timeoutMs: 5000 },
      rateLimit: { requestsPerSecond: 200, burst: 400, maxTrackedCallers: 10_000 },
      shutdown: { drainMs: 5000, timeoutMs: 10_000 },
      http: { apiDocsEnabled: false, trustProxy: false },
      markets: parseMarkets(DEFAULT_PLATFORM_MARKETS),
      storage: { driver: StorageDriver.MONGO, uri: MONGO_URI, database: 'clients' },
      outbox: { relayIntervalMs: 250, batchSize: 100, leaseMs: 30_000 },
      kafka: { bootstrapServers: [], changesTopic: 'clients.changed.v1' },
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
      AUTH_ADMIN_ROLE: 'master-data-admin',
      PLATFORM_MARKETS: 'MX:MXN:es-MX,CL:CLP:es-CL',
      PLATFORM_CURRENCIES: 'MXN:2,CLP:0',
      MONGODB_URI: 'mongodb+srv://user:secret@cluster.example.net/',
      MONGODB_DATABASE: 'master',
      OUTBOX_RELAY_INTERVAL_MS: '500',
      OUTBOX_BATCH_SIZE: '20',
      OUTBOX_LEASE_MS: '10000',
      KAFKA_BOOTSTRAP_SERVERS: 'kafka-1:9092, kafka-2:9092,',
      KAFKA_TOPIC_CHANGES: 'clients.changed.v2',
    });

    expect(config).toEqual({
      port: 8082,
      logLevel: LogLevel.DEBUG,
      auth: {
        enabled: true,
        issuer: AUTH_ENV.AUTH_ISSUER,
        jwksUrl: AUTH_ENV.AUTH_JWKS_URL,
        requiredRole: 'custom-role',
        adminRole: 'master-data-admin',
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
      markets: [
        { code: 'MX', currency: 'MXN', locale: 'es-MX' },
        { code: 'CL', currency: 'CLP', locale: 'es-CL' },
      ],
      storage: {
        driver: StorageDriver.MONGO,
        uri: 'mongodb+srv://user:secret@cluster.example.net/',
        database: 'master',
      },
      outbox: { relayIntervalMs: 500, batchSize: 20, leaseMs: 10_000 },
      kafka: {
        bootstrapServers: ['kafka-1:9092', 'kafka-2:9092'],
        changesTopic: 'clients.changed.v2',
      },
    });
  });

  it('should_disable_auth_without_urls_when_auth_enabled_is_false', () => {
    expect(loadConfig({ ...BASE_ENV, AUTH_ENABLED: 'false' }).auth).toEqual({ enabled: false });
  });

  it('should_fail_closed_when_auth_is_enabled_without_issuer_or_jwks', () => {
    expect(() => loadConfig(BASE_ENV)).toThrow(
      new InvalidConfigurationError([
        'AUTH_ISSUER: is required when AUTH_ENABLED=true',
        'AUTH_JWKS_URL: is required when AUTH_ENABLED=true',
      ]),
    );
    expect(() => loadConfig({ ...BASE_ENV, AUTH_ISSUER: AUTH_ENV.AUTH_ISSUER })).toThrow(
      'Invalid configuration: AUTH_JWKS_URL: is required when AUTH_ENABLED=true',
    );
  });

  it('should_refuse_unsafe_switches_in_production', () => {
    expect(() =>
      loadConfig({
        ...BASE_ENV,
        NODE_ENV: 'production',
        AUTH_ENABLED: 'false',
        FAULT_INJECTION_ENABLED: 'true',
        API_DOCS_ENABLED: 'true',
      }),
    ).toThrow(
      'Invalid configuration: AUTH_ENABLED=false is not allowed when NODE_ENV=production; ' +
        'FAULT_INJECTION_ENABLED=true is not allowed when NODE_ENV=production; ' +
        'API_DOCS_ENABLED=true is not allowed when NODE_ENV=production; ' +
        'KAFKA_BOOTSTRAP_SERVERS is required when NODE_ENV=production',
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

  it('should_use_in_memory_storage_without_mongodb_when_requested', () => {
    const config = loadConfig({ AUTH_ENABLED: 'false', STORAGE_DRIVER: 'memory' });

    expect(config.storage).toEqual({ driver: StorageDriver.MEMORY });
  });

  it('should_refuse_in_memory_storage_in_production', () => {
    expect(() =>
      loadConfig({
        ...AUTH_ENV,
        NODE_ENV: 'production',
        STORAGE_DRIVER: 'memory',
        KAFKA_BOOTSTRAP_SERVERS: 'k:9092',
      }),
    ).toThrow(
      'Invalid configuration: STORAGE_DRIVER=memory is not allowed when NODE_ENV=production',
    );
  });

  it('should_start_in_production_with_secure_defaults', () => {
    const config = loadConfig({
      ...AUTH_ENV,
      NODE_ENV: 'production',
      KAFKA_BOOTSTRAP_SERVERS: 'kafka:9092',
    });

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
    [{ MONGODB_URI: undefined }, 'MONGODB_URI: is required when STORAGE_DRIVER=mongo'],
    [{ STORAGE_DRIVER: 'redis' }, 'STORAGE_DRIVER: Invalid option'],
    [{ MONGODB_URI: 'postgres://db' }, 'MONGODB_URI: must be a mongodb:// or mongodb+srv://'],
    [{ MONGODB_DATABASE: ' ' }, 'MONGODB_DATABASE: Too small'],
    [{ PLATFORM_MARKETS: 'MX:MXN' }, 'PLATFORM_MARKETS: MarketCatalogError: Invalid market'],
    [{ PLATFORM_MARKETS: '' }, 'at least one market is required'],
    [{ OUTBOX_BATCH_SIZE: '0' }, 'OUTBOX_BATCH_SIZE: Too small'],
    [{ OUTBOX_LEASE_MS: 'x' }, 'OUTBOX_LEASE_MS: Invalid input'],
    [{ OUTBOX_RELAY_INTERVAL_MS: '-1' }, 'OUTBOX_RELAY_INTERVAL_MS: Too small'],
    [{ KAFKA_TOPIC_CHANGES: ' ' }, 'KAFKA_TOPIC_CHANGES: Too small'],
    [{ AUTH_ADMIN_ROLE: '' }, 'AUTH_ADMIN_ROLE: Too small'],
  ])('should_reject_invalid_environment_%j', (overrides, message) => {
    const load = (): unknown => loadConfig({ ...AUTH_ENV, ...overrides });

    expect(load).toThrow(InvalidConfigurationError);
    expect(load).toThrow(message);
  });
});
