import { z } from 'zod';
import { parseFaultRules } from '../shared/fault-injection/fault-rule';
import { AppConfig, AuthConfig, LogLevel } from './app-config';

export const CONFIG_DEFAULTS = {
  port: 3000,
  logLevel: LogLevel.INFO,
  authEnabled: 'true',
  requiredRole: 'clients-reader',
  faultRules: '',
  faultTimeoutMs: 5000,
  rateLimitRps: 200,
  rateLimitBurst: 400,
} as const;

const MAX_PORT = 65_535;
const TRUE_VALUE = 'true';
const BOOLEAN_VALUES = [TRUE_VALUE, 'false'] as const;
const AUTH_SETTINGS_REQUIRED = 'is required when AUTH_ENABLED=true';

export type ConfigSource = Readonly<Record<string, string | undefined>>;

export class InvalidConfigurationError extends Error {
  constructor(issues: readonly string[]) {
    super(`Invalid configuration: ${issues.join('; ')}`);
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
      context.addIssue({ code: 'custom', message: String(error) });
      return z.NEVER;
    }
  });

const environmentSchema = z
  .object({
    PORT: z.coerce.number().int().min(1).max(MAX_PORT).default(CONFIG_DEFAULTS.port),
    LOG_LEVEL: z.enum(LogLevel).default(CONFIG_DEFAULTS.logLevel),
    AUTH_ENABLED: z.enum(BOOLEAN_VALUES).default(CONFIG_DEFAULTS.authEnabled),
    AUTH_ISSUER: z.url().optional(),
    AUTH_JWKS_URL: z.url().optional(),
    AUTH_REQUIRED_ROLE: z.string().min(1).default(CONFIG_DEFAULTS.requiredRole),
    FAULT_RULES: faultRulesSchema,
    FAULT_TIMEOUT_MS: z.coerce.number().int().positive().default(CONFIG_DEFAULTS.faultTimeoutMs),
    RATE_LIMIT_RPS: z.coerce.number().positive().default(CONFIG_DEFAULTS.rateLimitRps),
    RATE_LIMIT_BURST: z.coerce.number().int().positive().default(CONFIG_DEFAULTS.rateLimitBurst),
  })
  .superRefine((environment, context) => {
    if (environment.AUTH_ENABLED !== TRUE_VALUE) {
      return;
    }
    for (const key of ['AUTH_ISSUER', 'AUTH_JWKS_URL'] as const) {
      if (environment[key] === undefined) {
        context.addIssue({ code: 'custom', path: [key], message: AUTH_SETTINGS_REQUIRED });
      }
    }
  });

type Environment = z.infer<typeof environmentSchema>;

function toAuthConfig(environment: Environment): AuthConfig {
  const { AUTH_ENABLED, AUTH_ISSUER, AUTH_JWKS_URL, AUTH_REQUIRED_ROLE } = environment;
  if (AUTH_ENABLED !== TRUE_VALUE || AUTH_ISSUER === undefined || AUTH_JWKS_URL === undefined) {
    return { enabled: false };
  }
  return {
    enabled: true,
    issuer: AUTH_ISSUER,
    jwksUrl: AUTH_JWKS_URL,
    requiredRole: AUTH_REQUIRED_ROLE,
  };
}

export function issuesOf(error: z.ZodError): string[] {
  return error.issues.map((issue) => `${issue.path.join('.')}: ${issue.message}`);
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
    faultInjection: { rules: environment.FAULT_RULES, timeoutMs: environment.FAULT_TIMEOUT_MS },
    rateLimit: {
      requestsPerSecond: environment.RATE_LIMIT_RPS,
      burst: environment.RATE_LIMIT_BURST,
    },
  };
}
