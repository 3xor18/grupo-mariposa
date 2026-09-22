import { z } from 'zod';
import {
  BOOLEAN_VALUES,
  FALSE_VALUE,
  PRODUCTION_ENVIRONMENT,
  TRUE_VALUE,
} from '../shared/constants/environment.constants';
import { parseFaultRules } from '../shared/fault-injection/fault-rule';
import { AppConfig, AuthConfig, FaultInjectionConfig, LogLevel } from './app-config';

export const CONFIG_DEFAULTS = Object.freeze({
  port: 3000,
  logLevel: LogLevel.INFO,
  authEnabled: TRUE_VALUE,
  requiredRole: 'clients-reader',
  faultInjectionEnabled: FALSE_VALUE,
  faultRules: '',
  faultTimeoutMs: 5000,
  rateLimitRps: 200,
  rateLimitBurst: 400,
  rateLimitMaxTrackedCallers: 10_000,
  shutdownDrainMs: 5000,
  shutdownTimeoutMs: 10_000,
});

export const CONFIG_MESSAGES = Object.freeze({
  authSettingRequired: 'is required when AUTH_ENABLED=true',
  faultInjectionForbidden: 'must not be true when NODE_ENV=production',
});

const MAX_PORT = 65_535;
const ISSUE_SEPARATOR = '; ';
const PATH_SEPARATOR = '.';
const CUSTOM_ISSUE = 'custom';
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

const positiveInteger = (fallback: number): z.ZodDefault<z.ZodCoercedNumber> =>
  z.coerce.number().int().positive().default(fallback);

const environmentSchema = z.object({
  NODE_ENV: z.string().optional(),
  PORT: z.coerce.number().int().min(1).max(MAX_PORT).default(CONFIG_DEFAULTS.port),
  LOG_LEVEL: z.enum(LogLevel).default(CONFIG_DEFAULTS.logLevel),
  AUTH_ENABLED: z.enum(BOOLEAN_VALUES).default(CONFIG_DEFAULTS.authEnabled),
  AUTH_ISSUER: z.url().optional(),
  AUTH_JWKS_URL: z.url().optional(),
  AUTH_AUDIENCE: z.string().min(1).optional(),
  AUTH_REQUIRED_ROLE: z.string().min(1).default(CONFIG_DEFAULTS.requiredRole),
  FAULT_INJECTION_ENABLED: z.enum(BOOLEAN_VALUES).default(CONFIG_DEFAULTS.faultInjectionEnabled),
  FAULT_RULES: faultRulesSchema,
  FAULT_TIMEOUT_MS: positiveInteger(CONFIG_DEFAULTS.faultTimeoutMs),
  RATE_LIMIT_RPS: z.coerce.number().positive().default(CONFIG_DEFAULTS.rateLimitRps),
  RATE_LIMIT_BURST: positiveInteger(CONFIG_DEFAULTS.rateLimitBurst),
  RATE_LIMIT_MAX_TRACKED_CALLERS: positiveInteger(CONFIG_DEFAULTS.rateLimitMaxTrackedCallers),
  SHUTDOWN_DRAIN_MS: z.coerce.number().int().min(0).default(CONFIG_DEFAULTS.shutdownDrainMs),
  SHUTDOWN_TIMEOUT_MS: positiveInteger(CONFIG_DEFAULTS.shutdownTimeoutMs),
});

type Environment = z.infer<typeof environmentSchema>;

function missingAuthIssues(environment: Environment): string[] {
  return REQUIRED_AUTH_KEYS.filter((key) => environment[key] === undefined).map(
    (key) => `${key}: ${CONFIG_MESSAGES.authSettingRequired}`,
  );
}

function toAuthConfig(environment: Environment): AuthConfig {
  const { AUTH_ENABLED, AUTH_ISSUER, AUTH_JWKS_URL, AUTH_AUDIENCE } = environment;
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
    ...(AUTH_AUDIENCE === undefined ? {} : { audience: AUTH_AUDIENCE }),
  };
}

function toFaultInjectionConfig(environment: Environment): FaultInjectionConfig {
  const enabled = environment.FAULT_INJECTION_ENABLED === TRUE_VALUE;
  if (enabled && environment.NODE_ENV === PRODUCTION_ENVIRONMENT) {
    throw new InvalidConfigurationError([
      `FAULT_INJECTION_ENABLED: ${CONFIG_MESSAGES.faultInjectionForbidden}`,
    ]);
  }
  return { enabled, rules: environment.FAULT_RULES, timeoutMs: environment.FAULT_TIMEOUT_MS };
}

export function issuesOf(error: z.ZodError): string[] {
  return error.issues.map((issue) => `${issue.path.join(PATH_SEPARATOR)}: ${issue.message}`);
}

export function loadConfig(source: ConfigSource): AppConfig {
  const result = environmentSchema.safeParse(source);
  if (!result.success) {
    throw new InvalidConfigurationError(issuesOf(result.error));
  }
  const environment = result.data;
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
  };
}
