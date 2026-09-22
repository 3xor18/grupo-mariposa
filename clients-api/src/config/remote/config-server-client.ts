import { ConfigServerSettings, propertiesUrlOf } from './config-server-settings';
import { parseProperties, Properties } from './properties';

export const RETRY_BACKOFF_BASE_MS = 200;
const BACKOFF_FACTOR = 2;
const AUTHORIZATION_HEADER = 'Authorization';
const ACCEPT_HEADER = 'Accept';
const PROPERTIES_MEDIA_TYPE = 'text/plain';
const BASIC_SCHEME = 'Basic';
const CREDENTIALS_SEPARATOR = ':';
const LOWEST_CLIENT_ERROR = 400;
const LOWEST_SERVER_ERROR = 500;

export type Sleep = (milliseconds: number) => Promise<void>;

export interface ConfigServerTransport {
  readonly fetch: typeof fetch;
  readonly sleep: Sleep;
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
  const headers: Record<string, string> = { [ACCEPT_HEADER]: PROPERTIES_MEDIA_TYPE };
  if (settings.username !== undefined) {
    const credentials = `${settings.username}${CREDENTIALS_SEPARATOR}${settings.password ?? ''}`;
    headers[AUTHORIZATION_HEADER] =
      `${BASIC_SCHEME} ${Buffer.from(credentials).toString('base64')}`;
  }
  return headers;
}

function backoffFor(attempt: number): number {
  return RETRY_BACKOFF_BASE_MS * BACKOFF_FACTOR ** attempt;
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
  const retryable = response.status < LOWEST_CLIENT_ERROR || response.status >= LOWEST_SERVER_ERROR;
  throw retryable ? new Error(reason) : new NonRetryableResponseError(reason);
}

export async function fetchRemoteProperties(
  settings: ConfigServerSettings,
  transport: ConfigServerTransport,
): Promise<Properties> {
  const maxAttempts = settings.retries + 1;
  for (let attempt = 1; ; attempt += 1) {
    try {
      return await fetchOnce(settings, transport);
    } catch (error: unknown) {
      if (error instanceof NonRetryableResponseError || attempt >= maxAttempts) {
        throw new ConfigServerUnavailableError(propertiesUrlOf(settings), attempt, String(error));
      }
      await transport.sleep(backoffFor(attempt - 1));
    }
  }
}
