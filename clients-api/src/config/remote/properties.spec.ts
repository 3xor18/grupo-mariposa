import {
  isSensitiveKey,
  parseProperties,
  REDACTED_VALUE,
  redactSensitive,
  toEnvironmentKey,
  toEnvironmentStyle,
} from './properties';

describe('parseProperties', () => {
  it('should_parse_spring_config_server_colon_space_output', () => {
    const text = [
      'rate-limit.rps: 200',
      'auth.jwks-url: http://keycloak:8080/realms/mariposa/protocol/openid-connect/certs',
      'fault.rules: CLI-40001:503:2,CLI-40002:503',
    ].join('\n');

    expect(parseProperties(text)).toEqual({
      'rate-limit.rps': '200',
      'auth.jwks-url': 'http://keycloak:8080/realms/mariposa/protocol/openid-connect/certs',
      'fault.rules': 'CLI-40001:503:2,CLI-40002:503',
    });
  });

  it('should_accept_equals_colon_and_whitespace_separators_with_surrounding_spaces', () => {
    const text = 'a=1\nb = 2\nc:3\nd   4\n  e =  5  \nf =\ng';

    expect(parseProperties(text)).toEqual({ a: '1', b: '2', c: '3', d: '4', e: '5', f: '', g: '' });
  });

  it('should_ignore_blank_and_comment_lines_and_handle_crlf', () => {
    const text = '# comment\r\n! other comment\r\n\r\n   \r\nkey=value\r\n';

    expect(parseProperties(text)).toEqual({ key: 'value' });
  });

  it('should_unescape_separators_backslashes_and_control_characters', () => {
    const text = [
      String.raw`path\:with\=chars=a\:b\=c`,
      String.raw`windows=C\:\\temp\\logs`,
      String.raw`multi=line\nbreak\ttab`,
      String.raw`space\ key=value`,
    ].join('\n');

    expect(parseProperties(text)).toEqual({
      'path:with=chars': 'a:b=c',
      windows: String.raw`C:\temp\logs`,
      multi: 'line\nbreak\ttab',
      'space key': 'value',
    });
  });

  it('should_keep_last_value_when_key_is_repeated', () => {
    expect(parseProperties('a=1\na=2')).toEqual({ a: '2' });
  });
});

describe('property key helpers', () => {
  it.each([
    ['rate-limit.rps', 'RATE_LIMIT_RPS'],
    ['fault.rules', 'FAULT_RULES'],
    ['auth.jwks-url', 'AUTH_JWKS_URL'],
    ['PORT', 'PORT'],
  ])('should_map_%s_to_%s', (key, expected) => {
    expect(toEnvironmentKey(key)).toBe(expected);
  });

  it('should_convert_every_key_to_environment_style', () => {
    expect(toEnvironmentStyle({ 'log.level': 'debug' })).toEqual({ LOG_LEVEL: 'debug' });
  });

  it.each([
    ['CLIENT_SECRET', true],
    ['db.password', true],
    ['API_KEY', true],
    ['access-token', true],
    ['RATE_LIMIT_RPS', false],
  ])('should_flag_%s_sensitive_as_%s', (key, expected) => {
    expect(isSensitiveKey(key)).toBe(expected);
  });

  it('should_redact_only_sensitive_values', () => {
    expect(redactSensitive({ DB_PASSWORD: 'p4ss', PORT: '3000' })).toEqual({
      DB_PASSWORD: REDACTED_VALUE,
      PORT: '3000',
    });
  });
});
