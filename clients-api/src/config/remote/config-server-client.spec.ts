import { ConfigServerStub } from '../../../test/support/config-server';
import { LogLevel } from '../app-config';
import {
  ConfigServerTransport,
  ConfigServerUnavailableError,
  fetchRemoteProperties,
  RETRY_BACKOFF_BASE_MS,
} from './config-server-client';
import { ConfigServerSettings } from './config-server-settings';

describe('fetchRemoteProperties', () => {
  let stub: ConfigServerStub;
  const sleep = jest.fn<Promise<void>, [number]>(() => Promise.resolve());
  const transport: ConfigServerTransport = { fetch: (input, init) => fetch(input, init), sleep };

  const settings = (overrides: Partial<ConfigServerSettings> = {}): ConfigServerSettings => ({
    url: stub.url,
    appName: 'clients-api',
    profile: 'docker',
    timeoutMs: 1000,
    retries: 2,
    failFast: false,
    logLevel: LogLevel.SILENT,
    ...overrides,
  });

  const failureOf = async (action: Promise<unknown>): Promise<ConfigServerUnavailableError> => {
    const error: unknown = await action.catch((caught: unknown) => caught);
    if (!(error instanceof ConfigServerUnavailableError)) {
      throw new Error('expected ConfigServerUnavailableError');
    }
    return error;
  };

  beforeAll(async () => {
    stub = await ConfigServerStub.start();
  });

  afterAll(async () => {
    await stub.stop();
  });

  beforeEach(() => {
    sleep.mockClear();
  });

  it('should_fetch_and_parse_profile_properties_with_basic_auth', async () => {
    stub.respond({ status: 200, body: 'rate-limit.rps: 50\nfault.rules: CLI-1:503' });

    const properties = await fetchRemoteProperties(
      settings({ username: 'config', password: 's3cret' }),
      transport,
    );

    expect(properties).toEqual({ 'rate-limit.rps': '50', 'fault.rules': 'CLI-1:503' });
    expect(stub.requests[0]?.url).toBe('/clients-api-docker.properties');
    expect(stub.requests[0]?.headers.authorization).toBe(
      `Basic ${Buffer.from('config:s3cret').toString('base64')}`,
    );
  });

  it('should_send_empty_password_when_only_username_is_set', async () => {
    stub.respond({ status: 200, body: '' });

    await fetchRemoteProperties(settings({ username: 'config' }), transport);

    expect(stub.requests[0]?.headers.authorization).toBe(
      `Basic ${Buffer.from('config:').toString('base64')}`,
    );
  });

  it('should_not_send_authorization_without_credentials', async () => {
    stub.respond({ status: 200, body: 'a=1' });

    await fetchRemoteProperties(settings(), transport);

    expect(stub.requests[0]?.headers.authorization).toBeUndefined();
  });

  it('should_retry_with_exponential_backoff_then_succeed', async () => {
    stub.respond({ status: 503 }, { status: 500 }, { status: 200, body: 'a=1' });

    await expect(fetchRemoteProperties(settings(), transport)).resolves.toEqual({ a: '1' });
    expect(sleep.mock.calls).toEqual([[RETRY_BACKOFF_BASE_MS], [RETRY_BACKOFF_BASE_MS * 2]]);
  });

  it('should_give_up_after_configured_retries', async () => {
    stub.respond({ status: 503 }, { status: 503 }, { status: 503 }, { status: 200 });

    const error = await failureOf(fetchRemoteProperties(settings(), transport));

    expect(error.attempts).toBe(3);
    expect(error.message).toContain('unexpected status 503');
    expect(stub.requests).toHaveLength(3);
  });

  it('should_not_retry_client_errors', async () => {
    stub.respond({ status: 404 }, { status: 200 });

    const error = await failureOf(fetchRemoteProperties(settings(), transport));

    expect(error.attempts).toBe(1);
    expect(error.url).toBe(`${stub.url}/clients-api-docker.properties`);
    expect(sleep).not.toHaveBeenCalled();
  });

  it('should_abort_request_when_timeout_elapses', async () => {
    stub.respond({ status: 200, hang: true });

    const error = await failureOf(
      fetchRemoteProperties(settings({ timeoutMs: 50, retries: 0 }), transport),
    );

    expect(error.message).toMatch(/timeout|aborted/i);
  });

  it('should_report_network_failures', async () => {
    const rejecting: ConfigServerTransport = {
      fetch: () => Promise.reject(new Error('connection refused')),
      sleep,
    };

    await expect(fetchRemoteProperties(settings({ retries: 0 }), rejecting)).rejects.toThrow(
      'connection refused',
    );
  });
});
