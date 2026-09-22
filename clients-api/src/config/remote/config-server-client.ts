import { HTTP_HEADERS, PLAIN_TEXT_MEDIA_TYPE } from '../../shared/constants/http.constants';
import { SERVER_ERROR_STATUS_THRESHOLD } from '../../shared/errors/error-catalog';
import { ConfigServerSettings, propertiesUrlOf } from './config-server-settings';
import { parseProperties, Properties } from './properties';

export const RETRY_BACKOFF = Object.freeze({
  baseMs: 200,
  factor: 2,
  capMs: 5000,
  jitterRatio: 0.5,
});

const BASIC_SCHEME = 'Basic';
const BASE64_ENCODING = 'base64';
const CREDENTIALS_SEPARATOR = ':';
const CLIENT_ERROR_STATUS_THRESHOLD = 400;

export type Sleep = (milliseconds: number) => Promise<void>;

export interface ConfigServerTransport {
  readonly fetch: typeof fetch;
  readonly sleep: Sleep;
  readonly random: () => number;
}

export class ConfigServerUnavailableError extends Error {
  constructor(
    readonly url: string,
    readonly attempts: number,
    reason: string,
  ) {
    super(`Config server ${url} unavailable after ${String(attempts)} attempt(s): ${reason}`);
    this.name = ConfigServerUnavailableError.name;
  }
}

class NonRetryableResponseError extends Error {}

function headersFor(settings: ConfigServerSettings): Record<string, string> {
  const headers: Record<string, string> = { [HTTP_HEADERS.ACCEPT]: PLAIN_TEXT_MEDIA_TYPE };
  if (settings.username !== undefined) {
    const credentials = `${settings.username}${CREDENTIALS_SEPARATOR}${settings.password ?? ''}`;
    headers[HTTP_HEADERS.AUTHORIZATION] =
      `${BASIC_SCHEME} ${Buffer.from(credentials).toString(BASE64_ENCODING)}`;
  }
  return headers;
}

export function backoffFor(retry: number, random: () => number): number {
  const exponential = RETRY_BACKOFF.baseMs * RETRY_BACKOFF.factor ** retry;
  const capped = Math.min(RETRY_BACKOFF.capMs, exponential);
  const jitter = capped * RETRY_BACKOFF.jitterRatio * random();
  return Math.round(capped * (1 - RETRY_BACKOFF.jitterRatio) + jitter);
}

function isRetryableStatus(status: number): boolean {
  return status < CLIENT_ERROR_STATUS_THRESHOLD || status >= SERVER_ERROR_STATUS_THRESHOLD;
}

async function fetchOnce(
  settings: ConfigServerSettings,
  transport: ConfigServerTransport,
): Promise<Properties> {
  const response = await transport.fetch(propertiesUrlOf(settings), {
    headers: headersFor(settings),
    signal: AbortSignal.timeout(settings.timeoutMs),
  });
  if (response.ok) {
    return parseProperties(await response.text());
  }
  const reason = `unexpected status ${String(response.status)}`;
  throw isRetryableStatus(response.status)
    ? new Error(reason)
    : new NonRetryableResponseError(reason);
}

export async function fetchRemoteProperties(
  settings: ConfigServerSettings,
  transport: ConfigServerTransport,
): Promise<Properties> {
  const maxAttempts = Math.max(1, settings.retries + 1);
  let lastFailure: unknown;
  for (let attempt = 1; attempt <= maxAttempts; attempt += 1) {
    try {
      return await fetchOnce(settings, transport);
    } catch (error: unknown) {
      lastFailure = error;
      if (error instanceof NonRetryableResponseError) {
        throw new ConfigServerUnavailableError(propertiesUrlOf(settings), attempt, String(error));
      }
      if (attempt < maxAttempts) {
        await transport.sleep(backoffFor(attempt - 1, transport.random));
      }
    }
  }
  throw new ConfigServerUnavailableError(
    propertiesUrlOf(settings),
    maxAttempts,
    String(lastFailure),
  );
}
