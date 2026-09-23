import { FaultRule } from '../shared/fault-injection/fault-rule';
import { CurrencyDefinition } from '../shared/markets/currency-catalog';
import { MarketDefinition } from '../shared/markets/market-catalog';

export const APP_CONFIG = Symbol('APP_CONFIG');

export enum LogLevel {
  FATAL = 'fatal',
  ERROR = 'error',
  WARN = 'warn',
  INFO = 'info',
  DEBUG = 'debug',
  TRACE = 'trace',
  SILENT = 'silent',
}

export interface EnabledAuthConfig {
  readonly enabled: true;
  readonly issuer: string;
  readonly jwksUrl: string;
  readonly requiredRole: string;
  readonly adminRole: string;
  readonly audience: string;
}

export interface DisabledAuthConfig {
  readonly enabled: false;
}

export type AuthConfig = EnabledAuthConfig | DisabledAuthConfig;

export interface FaultInjectionConfig {
  readonly enabled: boolean;
  readonly rules: readonly FaultRule[];
  readonly timeoutMs: number;
}

export interface RateLimitConfig {
  readonly requestsPerSecond: number;
  readonly burst: number;
  readonly maxTrackedCallers: number;
}

export interface ShutdownConfig {
  readonly drainMs: number;
  readonly timeoutMs: number;
}

export type TrustProxySetting = boolean | number | string;

export interface HttpConfig {
  readonly apiDocsEnabled: boolean;
  readonly trustProxy: TrustProxySetting;
}

export enum StorageDriver {
  MONGO = 'mongo',
  MEMORY = 'memory',
}

export interface MongoStorageConfig {
  readonly driver: StorageDriver.MONGO;
  readonly uri: string;
  readonly database: string;
}

export interface MemoryStorageConfig {
  readonly driver: StorageDriver.MEMORY;
}

export type StorageConfig = MongoStorageConfig | MemoryStorageConfig;

export interface OutboxConfig {
  readonly relayIntervalMs: number;
  readonly batchSize: number;
  readonly leaseMs: number;
  readonly retryDelayMs: number;
  readonly retentionSeconds: number;
}

export interface KafkaConfig {
  readonly bootstrapServers: readonly string[];
  readonly changesTopic: string;
  readonly tlsEnabled: boolean;
}

export interface AppConfig {
  readonly port: number;
  readonly logLevel: LogLevel;
  readonly auth: AuthConfig;
  readonly faultInjection: FaultInjectionConfig;
  readonly rateLimit: RateLimitConfig;
  readonly shutdown: ShutdownConfig;
  readonly http: HttpConfig;
  readonly markets: readonly MarketDefinition[];
  readonly currencies: readonly CurrencyDefinition[];
  readonly storage: StorageConfig;
  readonly outbox: OutboxConfig;
  readonly kafka: KafkaConfig;
  readonly seedEnabled: boolean;
}
