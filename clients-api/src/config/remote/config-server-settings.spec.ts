import { LogLevel } from '../app-config';
import { InvalidConfigurationError } from '../load-config';
import { loadConfigServerSettings, propertiesUrlOf } from './config-server-settings';

describe('loadConfigServerSettings', () => {
  it.each([{}, { CONFIG_SERVER_URL: '' }])('should_skip_when_url_is_absent_%#', (source) => {
    expect(loadConfigServerSettings(source)).toBeUndefined();
  });

  it('should_apply_defaults_when_only_url_is_set', () => {
    expect(loadConfigServerSettings({ CONFIG_SERVER_URL: 'http://config:8888/' })).toEqual({
      url: 'http://config:8888',
      appName: 'clients-api',
      profile: 'default',
      timeoutMs: 3000,
      retries: 3,
      failFast: false,
      logLevel: LogLevel.INFO,
    });
  });

  it('should_read_every_setting_when_provided', () => {
    const settings = loadConfigServerSettings({
      CONFIG_SERVER_URL: 'http://config:8888',
      CONFIG_APP_NAME: 'clients',
      CONFIG_PROFILE: 'docker',
      CONFIG_SERVER_USERNAME: 'config',
      CONFIG_SERVER_PASSWORD: 'secret',
      CONFIG_SERVER_TIMEOUT_MS: '500',
      CONFIG_SERVER_RETRIES: '0',
      CONFIG_SERVER_FAIL_FAST: 'true',
      LOG_LEVEL: 'debug',
    });

    expect(settings).toEqual({
      url: 'http://config:8888',
      appName: 'clients',
      profile: 'docker',
      username: 'config',
      password: 'secret',
      timeoutMs: 500,
      retries: 0,
      failFast: true,
      logLevel: LogLevel.DEBUG,
    });
  });

  it('should_fallback_to_info_log_level_when_level_is_unknown', () => {
    const settings = loadConfigServerSettings({ CONFIG_SERVER_URL: 'http://c', LOG_LEVEL: 'x' });

    expect(settings?.logLevel).toBe(LogLevel.INFO);
  });

  it.each([
    { CONFIG_SERVER_URL: 'not a url' },
    { CONFIG_SERVER_URL: 'http://c', CONFIG_SERVER_TIMEOUT_MS: '0' },
    { CONFIG_SERVER_URL: 'http://c', CONFIG_SERVER_RETRIES: '-1' },
    { CONFIG_SERVER_URL: 'http://c', CONFIG_SERVER_FAIL_FAST: 'maybe' },
    { CONFIG_SERVER_URL: 'http://c', CONFIG_SERVER_RETRIES: '11' },
    { CONFIG_SERVER_URL: 'http://c', CONFIG_SERVER_TIMEOUT_MS: '60001' },
  ])('should_fail_fast_on_invalid_settings_%#', (source) => {
    expect(() => loadConfigServerSettings(source)).toThrow(InvalidConfigurationError);
  });
});

describe('propertiesUrlOf', () => {
  it('should_build_spring_properties_url_without_embedded_credentials', () => {
    const settings = loadConfigServerSettings({
      CONFIG_SERVER_URL: 'http://user:pass@config:8888/base',
      CONFIG_PROFILE: 'docker',
    });

    expect(settings && propertiesUrlOf(settings)).toBe(
      'http://config:8888/base/clients-api-docker.properties',
    );
  });
});
