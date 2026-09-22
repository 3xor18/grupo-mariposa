import { createRemoteJWKSet, errors, JWTVerifyGetKey } from 'jose';
import {
  TEST_ISSUER,
  TEST_ROLE,
  TestIdentityProvider,
} from '../../../test/support/identity-provider';
import { EnabledAuthConfig } from '../../config/app-config';
import { ErrorCode } from '../errors/error-code.enum';
import { ProblemException } from '../errors/problem.exception';
import {
  AUTH_MESSAGES,
  extractBearerToken,
  hasRealmRole,
  JoseAccessTokenVerifier,
} from './jose-access-token-verifier';

async function problemOf(action: Promise<void>): Promise<ProblemException> {
  const error: unknown = await action.catch((caught: unknown) => caught);
  if (!(error instanceof ProblemException)) {
    throw new Error('expected a ProblemException');
  }
  return error;
}

describe('JoseAccessTokenVerifier', () => {
  let idp: TestIdentityProvider;
  let verifier: JoseAccessTokenVerifier;
  let settings: EnabledAuthConfig;

  beforeAll(async () => {
    idp = await TestIdentityProvider.start();
    settings = {
      enabled: true,
      issuer: TEST_ISSUER,
      jwksUrl: idp.jwksUrl,
      requiredRole: TEST_ROLE,
    };
    verifier = new JoseAccessTokenVerifier(settings, createRemoteJWKSet(new URL(idp.jwksUrl)));
  });

  afterAll(async () => {
    await idp.stop();
  });

  it('should_accept_valid_token_with_required_role', async () => {
    await expect(verifier.authenticate(`Bearer ${await idp.token()}`)).resolves.toBeUndefined();
  });

  it.each([undefined, '', 'Basic abc', 'Bearer not-a-jwt'])(
    'should_reject_missing_or_malformed_header_%p',
    async (header) => {
      const problem = await problemOf(verifier.authenticate(header));

      expect(problem.code).toBe(ErrorCode.UNAUTHORIZED);
      expect(problem.detail).toBe(AUTH_MESSAGES.missingToken);
    },
  );

  it.each([
    ['expired', { expired: true }],
    ['foreign issuer', { issuer: 'http://other/realms/x' }],
  ])('should_reject_%s_token_as_unauthorized', async (_case, options) => {
    const problem = await problemOf(verifier.authenticate(`Bearer ${await idp.token(options)}`));

    expect(problem.code).toBe(ErrorCode.UNAUTHORIZED);
    expect(problem.detail).toBe(AUTH_MESSAGES.invalidToken);
  });

  it('should_reject_token_signed_by_unknown_key', async () => {
    const token = await idp.token({ key: await TestIdentityProvider.foreignKey() });

    const problem = await problemOf(verifier.authenticate(`Bearer ${token}`));

    expect(problem.code).toBe(ErrorCode.UNAUTHORIZED);
  });

  it('should_reject_token_without_role_as_forbidden', async () => {
    const token = await idp.token({ roles: ['products-reader'] });

    const problem = await problemOf(verifier.authenticate(`Bearer ${token}`));

    expect(problem.code).toBe(ErrorCode.FORBIDDEN);
    expect(problem.detail).toBe(AUTH_MESSAGES.missingRole);
  });

  it.each([
    ['jwks timeout', new errors.JWKSTimeout()],
    ['network failure', new TypeError('fetch failed')],
  ])('should_report_identity_provider_unavailable_on_%s', async (_case, failure) => {
    const failingKeys: JWTVerifyGetKey = () => Promise.reject(failure);
    const unavailable = new JoseAccessTokenVerifier(settings, failingKeys);

    const problem = await problemOf(unavailable.authenticate(`Bearer ${await idp.token()}`));

    expect(problem.code).toBe(ErrorCode.SERVICE_UNAVAILABLE);
    expect(problem.detail).toBe(AUTH_MESSAGES.identityProviderUnavailable);
  });
});

describe('token helpers', () => {
  it('should_extract_token_only_from_bearer_scheme', () => {
    expect(extractBearerToken('Bearer a.b.c')).toBe('a.b.c');
    expect(extractBearerToken('bearer a.b.c')).toBeUndefined();
    expect(extractBearerToken(undefined)).toBeUndefined();
  });

  it.each([
    [{ realm_access: { roles: ['clients-reader'] } }, true],
    [{ realm_access: { roles: ['other'] } }, false],
    [{ realm_access: { roles: 'clients-reader' } }, false],
    [{ realm_access: {} }, false],
    [{ realm_access: null }, false],
    [{ realm_access: 'clients-reader' }, false],
    [{}, false],
  ])('should_check_realm_role_for_%j', (payload, expected) => {
    expect(hasRealmRole(payload, 'clients-reader')).toBe(expected);
  });
});
