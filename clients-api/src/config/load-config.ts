import { z } from 'zod';
import {
  BOOLEAN_VALUES,
  FALSE_VALUE,
  PRODUCTION_ENVIRONMENT,
  TRUE_VALUE,
} from '../shared/constants/environment.constants';
import { parseFaultRules } from '../shared/fault-injection/fault-rule';
import { DEFAULT_PLATFORM_MARKETS, parseMarkets } from '../shared/markets/market-catalog';
import {
  AppConfig,
  AuthConfig,
  FaultInjectionConfig,
  HttpConfig,
  KafkaConfig,
  LogLevel,
  OutboxConfig,
  StorageConfig,
  StorageDriver,
} from './app-config';

export const CONFIG_DEFAULTS = Object.freeze({
  port: 3000,
  logLevel: LogLevel.INFO,
  authEnabled: TRUE_VALUE,
  requiredRole: 'clients-reader',
  adminRole: 'clients-admin',
  audience: 'clients-api',
  faultInjectionEnabled: FALSE_VALUE,
  faultRules: '',
  faultTimeoutMs: 5000,
  rateLimitRps: 200,
  rateLimitBurst: 400,
  rateLimitMaxTrackedCallers: 10_000,
  shutdownDrainMs: 5000,
  shutdownTimeoutMs: 10_000,
  apiDocsEnabled: FALSE_VALUE,
  trustProxy: false,
  platformMarkets: DEFAULT_PLATFORM_MARKETS,
  storageDriver: StorageDriver.MONGO,
  mongoDatabase: 'clients',
  outboxRelayIntervalMs: 250,
  outboxBatchSize: 100,
  outboxLeaseMs: 30_000,
  kafkaBootstrapServers: '',
  kafkaChangesTopic: 'clients.changed.v1',
});

export const CONFIG_MESSAGES = Object.freeze({
  authSettingRequired: 'is required when AUTH_ENABLED=true',
  forbiddenInProduction: 'is not allowed when NODE_ENV=production',
  requiredInProduction: 'is required when NODE_ENV=production',
  mongoUriRequired: 'MONGODB_URI: is required when STORAGE_DRIVER=mongo',
  mongoScheme: 'must be a mongodb:// or mongodb+srv:// connection string',
});

const MAX_PORT = 65_535;
const ISSUE_SEPARATOR = '; ';
const PATH_SEPARATOR = '.';
const CUSTOM_ISSUE = 'custom';
const MONGODB_SCHEMES = Object.freeze(['mongodb://', 'mongodb+srv://']);
const LIST_SEPARATOR = ',';
const HOP_COUNT_PATTERN = /^\d+$/;
const REQUIRED_AUTH_KEYS = Object.freeze(['AUTH_ISSUER', 'AUTH_JWKS_URL'] as const);

export type ConfigSource = Readonly<Record<string, string | undefined>>;

export class InvalidConfigurationError extends Error {
  constructor(issues: readonly string[]) {
    super(`Invalid configuration: ${issues.join(ISSUE_SEPARATOR)}`);
    this.name = InvalidConfigurationError.name;
  }
}

const faultRulesSchema = z
  .string()
  .default(CONFIG_DEFAULTS.faultRules)
  .transform((raw, context) => {
    try {
      return parseFaultRules(raw);
    } catch (error: unknown) {
      context.addIssue({ code: CUSTOM_ISSUE, message: String(error) });
      return z.NEVER;
    }
  });

export const trustProxySchema = z
  .union([
    z.enum(BOOLEAN_VALUES).transform((value) => value === TRUE_VALUE),
    z.string().regex(HOP_COUNT_PATTERN).transform(Number),
    z.string().trim().min(1),
  ])
  .default(CONFIG_DEFAULTS.trustProxy);

const marketsSchema = z
  .string()
  .default(CONFIG_DEFAULTS.platformMarkets)
  .transform((raw, context) => {
    try {
      return parseMarkets(raw);
    } catch (error: unknown) {
      context.addIssue({ code: CUSTOM_ISSUE, message: String(error) });
      return z.NEVER;
    }
  });

const mongoUriSchema = z
  .string()
  .refine((uri) => MONGODB_SCHEMES.some((scheme) => uri.startsWith(scheme)), {
    message: CONFIG_MESSAGES.mongoScheme,
  });

const bootstrapServersSchema = z
  .string()
  .default(CONFIG_DEFAULTS.kafkaBootstrapServers)
  .transform((raw) =>
    raw
      .split(LIST_SEPARATOR)
      .map((server) => server.trim())
      .filter((server) => server.length > 0),
  );

const positiveInteger = (fallback: number): z.ZodDefault<z.ZodCoercedNumber> =>
  z.coerce.number().int().positive().default(fallback);

const environmentSchema = z.object({
  NODE_ENV: z.string().optional(),
  PORT: z.coerce.number().int().min(1).max(MAX_PORT).default(CONFIG_DEFAULTS.port),
  LOG_LEVEL: z.enum(LogLevel).default(CONFIG_DEFAULTS.logLevel),
  AUTH_ENABLED: z.enum(BOOLEAN_VALUES).default(CONFIG_DEFAULTS.authEnabled),
  AUTH_ISSUER: z.url().optional(),
  AUTH_JWKS_URL: z.url().optional(),
  AUTH_AUDIENCE: z.string().trim().min(1).default(CONFIG_DEFAULTS.audience),
  AUTH_REQUIRED_ROLE: z.string().min(1).default(CONFIG_DEFAULTS.requiredRole),
  AUTH_ADMIN_ROLE: z.string().min(1).default(CONFIG_DEFAULTS.adminRole),
  FAULT_INJECTION_ENABLED: z.enum(BOOLEAN_VALUES).default(CONFIG_DEFAULTS.faultInjectionEnabled),
  FAULT_RULES: faultRulesSchema,
  FAULT_TIMEOUT_MS: positiveInteger(CONFIG_DEFAULTS.faultTimeoutMs),
  RATE_LIMIT_RPS: z.coerce.number().positive().default(CONFIG_DEFAULTS.rateLimitRps),
  RATE_LIMIT_BURST: positiveInteger(CONFIG_DEFAULTS.rateLimitBurst),
  RATE_LIMIT_MAX_TRACKED_CALLERS: positiveInteger(CONFIG_DEFAULTS.rateLimitMaxTrackedCallers),
  SHUTDOWN_DRAIN_MS: z.coerce.number().int().min(0).default(CONFIG_DEFAULTS.shutdownDrainMs),
  SHUTDOWN_TIMEOUT_MS: positiveInteger(CONFIG_DEFAULTS.shutdownTimeoutMs),
  API_DOCS_ENABLED: z.enum(BOOLEAN_VALUES).default(CONFIG_DEFAULTS.apiDocsEnabled),
  TRUST_PROXY: trustProxySchema,
  PLATFORM_MARKETS: marketsSchema,
  STORAGE_DRIVER: z.enum(StorageDriver).default(CONFIG_DEFAULTS.storageDriver),
  MONGODB_URI: mongoUriSchema.optional(),
  MONGODB_DATABASE: z.string().trim().min(1).default(CONFIG_DEFAULTS.mongoDatabase),
  OUTBOX_RELAY_INTERVAL_MS: positiveInteger(CONFIG_DEFAULTS.outboxRelayIntervalMs),
  OUTBOX_BATCH_SIZE: positiveInteger(CONFIG_DEFAULTS.outboxBatchSize),
  OUTBOX_LEASE_MS: positiveInteger(CONFIG_DEFAULTS.outboxLeaseMs),
  KAFKA_BOOTSTRAP_SERVERS: bootstrapServersSchema,
  KAFKA_TOPIC_CHANGES: z.string().trim().min(1).default(CONFIG_DEFAULTS.kafkaChangesTopic),
});

type Environment = z.infer<typeof environmentSchema>;

const PRODUCTION_FORBIDDEN_VALUES = Object.freeze([
  ['AUTH_ENABLED', FALSE_VALUE],
  ['FAULT_INJECTION_ENABLED', TRUE_VALUE],
  ['API_DOCS_ENABLED', TRUE_VALUE],
  ['STORAGE_DRIVER', StorageDriver.MEMORY],
] as const);

function productionIssues(environment: Environment): string[] {
  if (environment.NODE_ENV !== PRODUCTION_ENVIRONMENT) {
    return [];
  }
  const forbidden = PRODUCTION_FORBIDDEN_VALUES.filter(
    ([key, value]) => environment[key] === value,
  ).map(([key, value]) => `${key}=${value} ${CONFIG_MESSAGES.forbiddenInProduction}`);
  const missingKafka =
    environment.KAFKA_BOOTSTRAP_SERVERS.length === 0
      ? [`KAFKA_BOOTSTRAP_SERVERS ${CONFIG_MESSAGES.requiredInProduction}`]
      : [];
  return [...forbidden, ...missingKafka];
}

function missingAuthIssues(environment: Environment): string[] {
  return REQUIRED_AUTH_KEYS.filter((key) => environment[key] === undefined).map(
    (key) => `${key}: ${CONFIG_MESSAGES.authSettingRequired}`,
  );
}

function toAuthConfig(environment: Environment): AuthConfig {
  const { AUTH_ENABLED, AUTH_ISSUER, AUTH_JWKS_URL } = environment;
  if (AUTH_ENABLED !== TRUE_VALUE) {
    return { enabled: false };
  }
  if (AUTH_ISSUER === undefined || AUTH_JWKS_URL === undefined) {
    throw new InvalidConfigurationError(missingAuthIssues(environment));
  }
  return {
    enabled: true,
    issuer: AUTH_ISSUER,
    jwksUrl: AUTH_JWKS_URL,
    requiredRole: environment.AUTH_REQUIRED_ROLE,
    adminRole: environment.AUTH_ADMIN_ROLE,
    audience: environment.AUTH_AUDIENCE,
  };
}

function toHttpConfig(environment: Environment): HttpConfig {
  return {
    apiDocsEnabled: environment.API_DOCS_ENABLED === TRUE_VALUE,
    trustProxy: environment.TRUST_PROXY,
  };
}

function toFaultInjectionConfig(environment: Environment): FaultInjectionConfig {
  return {
    enabled: environment.FAULT_INJECTION_ENABLED === TRUE_VALUE,
    rules: environment.FAULT_RULES,
    timeoutMs: environment.FAULT_TIMEOUT_MS,
  };
}

function toOutboxConfig(environment: Environment): OutboxConfig {
  return {
    relayIntervalMs: environment.OUTBOX_RELAY_INTERVAL_MS,
    batchSize: environment.OUTBOX_BATCH_SIZE,
    leaseMs: environment.OUTBOX_LEASE_MS,
  };
}

function toStorageConfig(environment: Environment): StorageConfig {
  if (environment.STORAGE_DRIVER === StorageDriver.MEMORY) {
    return { driver: StorageDriver.MEMORY };
  }
  if (environment.MONGODB_URI === undefined) {
    throw new InvalidConfigurationError([CONFIG_MESSAGES.mongoUriRequired]);
  }
  return {
    driver: StorageDriver.MONGO,
    uri: environment.MONGODB_URI,
    database: environment.MONGODB_DATABASE,
  };
}

function toKafkaConfig(environment: Environment): KafkaConfig {
  return {
    bootstrapServers: environment.KAFKA_BOOTSTRAP_SERVERS,
    changesTopic: environment.KAFKA_TOPIC_CHANGES,
  };
}

export function issuesOf(error: z.ZodError): string[] {
  return error.issues.map((issue) => `${issue.path.join(PATH_SEPARATOR)}: ${issue.message}`);
}

function parseEnvironment(source: ConfigSource): Environment {
  const result = environmentSchema.safeParse(source);
  if (!result.success) {
    throw new InvalidConfigurationError(issuesOf(result.error));
  }
  const issues = productionIssues(result.data);
  if (issues.length > 0) {
    throw new InvalidConfigurationError(issues);
  }
  return result.data;
}

export function loadConfig(source: ConfigSource): AppConfig {
  const environment = parseEnvironment(source);
  return {
    port: environment.PORT,
    logLevel: environment.LOG_LEVEL,
    auth: toAuthConfig(environment),
    faultInjection: toFaultInjectionConfig(environment),
    rateLimit: {
      requestsPerSecond: environment.RATE_LIMIT_RPS,
      burst: environment.RATE_LIMIT_BURST,
      maxTrackedCallers: environment.RATE_LIMIT_MAX_TRACKED_CALLERS,
    },
    shutdown: {
      drainMs: environment.SHUTDOWN_DRAIN_MS,
      timeoutMs: environment.SHUTDOWN_TIMEOUT_MS,
    },
    http: toHttpConfig(environment),
    markets: environment.PLATFORM_MARKETS,
    storage: toStorageConfig(environment),
    outbox: toOutboxConfig(environment),
    kafka: toKafkaConfig(environment),
  };
}
