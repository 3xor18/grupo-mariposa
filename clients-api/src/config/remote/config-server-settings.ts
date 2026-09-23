import { z } from 'zod';
import {
  BOOLEAN_VALUES,
  FALSE_VALUE,
  TRUE_VALUE,
} from '../../shared/constants/environment.constants';
import { SERVICE_NAME } from '../../shared/constants/logging.constants';
import { LogLevel } from '../app-config';
import { ConfigSource, InvalidConfigurationError, issuesOf } from '../load-config';

export const CONFIG_SERVER_DEFAULTS = Object.freeze({
  appName: SERVICE_NAME,
  profile: 'default',
  timeoutMs: 3000,
  retries: 3,
  failFast: FALSE_VALUE,
});

export const CONFIG_SERVER_LIMITS = Object.freeze({
  maxTimeoutMs: 60_000,
  maxRetries: 10,
});

const PROPERTIES_EXTENSION = '.properties';
const SEGMENT_SEPARATOR = '-';
const PATH_SEPARATOR = '/';

export interface ConfigServerSettings {
  readonly url: string;
  readonly appName: string;
  readonly profile: string;
  readonly username?: string;
  readonly password?: string;
  readonly timeoutMs: number;
  readonly retries: number;
  readonly failFast: boolean;
  readonly logLevel: LogLevel;
}

const optionalText = z
  .string()
  .optional()
  .transform((value) => (value === '' ? undefined : value));

const settingsSchema = z.object({
  CONFIG_SERVER_URL: optionalText.pipe(z.url().optional()),
  CONFIG_APP_NAME: z.string().min(1).default(CONFIG_SERVER_DEFAULTS.appName),
  CONFIG_PROFILE: z.string().min(1).default(CONFIG_SERVER_DEFAULTS.profile),
  CONFIG_SERVER_USERNAME: optionalText,
  CONFIG_SERVER_PASSWORD: optionalText,
  CONFIG_SERVER_TIMEOUT_MS: z.coerce
    .number()
    .int()
    .positive()
    .max(CONFIG_SERVER_LIMITS.maxTimeoutMs)
    .default(CONFIG_SERVER_DEFAULTS.timeoutMs),
  CONFIG_SERVER_RETRIES: z.coerce
    .number()
    .int()
    .min(0)
    .max(CONFIG_SERVER_LIMITS.maxRetries)
    .default(CONFIG_SERVER_DEFAULTS.retries),
  CONFIG_SERVER_FAIL_FAST: z.enum(BOOLEAN_VALUES).default(CONFIG_SERVER_DEFAULTS.failFast),
  LOG_LEVEL: z.enum(LogLevel).catch(LogLevel.INFO),
});

function withoutTrailingSeparators(url: string): string {
  let end = url.length;
  while (url.charAt(end - 1) === PATH_SEPARATOR) {
    end -= 1;
  }
  return url.slice(0, end);
}

type RawSettings = z.infer<typeof settingsSchema>;

function credentialsOf(raw: RawSettings): Pick<ConfigServerSettings, 'username' | 'password'> {
  return {
    ...(raw.CONFIG_SERVER_USERNAME === undefined ? {} : { username: raw.CONFIG_SERVER_USERNAME }),
    ...(raw.CONFIG_SERVER_PASSWORD === undefined ? {} : { password: raw.CONFIG_SERVER_PASSWORD }),
  };
}

export function loadConfigServerSettings(source: ConfigSource): ConfigServerSettings | undefined {
  const result = settingsSchema.safeParse(source);
  if (!result.success) {
    throw new InvalidConfigurationError(issuesOf(result.error));
  }
  const raw = result.data;
  if (raw.CONFIG_SERVER_URL === undefined) {
    return undefined;
  }
  return {
    url: withoutTrailingSeparators(raw.CONFIG_SERVER_URL),
    appName: raw.CONFIG_APP_NAME,
    profile: raw.CONFIG_PROFILE,
    ...credentialsOf(raw),
    timeoutMs: raw.CONFIG_SERVER_TIMEOUT_MS,
    retries: raw.CONFIG_SERVER_RETRIES,
    failFast: raw.CONFIG_SERVER_FAIL_FAST === TRUE_VALUE,
    logLevel: raw.LOG_LEVEL,
  };
}

export function propertiesUrlOf(settings: ConfigServerSettings): string {
  const document = [settings.appName, settings.profile].map(encodeURIComponent);
  const url = new URL(
    `${settings.url}${PATH_SEPARATOR}${document.join(SEGMENT_SEPARATOR)}${PROPERTIES_EXTENSION}`,
  );
  url.username = '';
  url.password = '';
  return url.toString();
}
