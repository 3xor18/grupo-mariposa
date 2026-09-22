import { FaultRule } from '../shared/fault-injection/fault-rule';

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
}

export interface DisabledAuthConfig {
  readonly enabled: false;
}

export type AuthConfig = EnabledAuthConfig | DisabledAuthConfig;

export interface FaultInjectionConfig {
  readonly rules: readonly FaultRule[];
  readonly timeoutMs: number;
}

export interface RateLimitConfig {
  readonly requestsPerSecond: number;
  readonly burst: number;
}

export interface AppConfig {
  readonly port: number;
  readonly logLevel: LogLevel;
  readonly auth: AuthConfig;
  readonly faultInjection: FaultInjectionConfig;
  readonly rateLimit: RateLimitConfig;
}
