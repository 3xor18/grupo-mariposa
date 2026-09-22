import { createRemoteJWKSet, errors, JWTVerifyGetKey, SignJWT } from 'jose';
import {
  TEST_AUDIENCE,
  TEST_CLIENT,
  TEST_ISSUER,
  TEST_ROLE,
  TestIdentityProvider,
} from '../../../test/support/identity-provider';
import { EnabledAuthConfig } from '../../config/app-config';
import { ErrorCode } from '../errors/error-code.enum';
import { ProblemException } from '../errors/problem.exception';
import { guardedKeySource, IdentityProviderUnavailableError } from './guarded-key-source';
import {
  ACCEPTED_ALGORITHMS,
  AUTH_MESSAGES,
  extractBearerToken,
  hasRealmRole,
  JoseAccessTokenVerifier,
  principalOf,
  REQUIRED_CLAIMS,
  toVerificationError,
} from './jose-access-token-verifier';

async function problemOf(action: Promise<unknown>): Promise<ProblemException> {
  const error: unknown = await action.catch((caught: unknown) => caught);
  if (!(error instanceof ProblemException)) {
    throw new Error(`expected a ProblemException, got ${String(error)}`);
  }
  return error;
}

describe('JoseAccessTokenVerifier', () => {
  let idp: TestIdentityProvider;
  let verifier: JoseAccessTokenVerifier;
  let settings: EnabledAuthConfig;

  const verifierWith = (
    overrides: Partial<EnabledAuthConfig> = {},
    timeoutDuration = 1000,
  ): JoseAccessTokenVerifier =>
    new JoseAccessTokenVerifier(
      { ...settings, ...overrides },
      guardedKeySource(createRemoteJWKSet(new URL(idp.jwksUrl), { timeoutDuration })),
    );

  const expectUnauthorized = async (token: string, detail: string): Promise<void> => {
    const problem = await problemOf(verifier.authenticate(`Bearer ${token}`));
    expect(problem.code).toBe(ErrorCode.UNAUTHORIZED);
    expect(problem.detail).toBe(detail);
  };

  beforeAll(async () => {
    idp = await TestIdentityProvider.start();
    settings = {
      enabled: true,
      issuer: TEST_ISSUER,
      jwksUrl: idp.jwksUrl,
      requiredRole: TEST_ROLE,
      audience: TEST_AUDIENCE,
    };
    verifier = verifierWith();
  });

  afterEach(() => {
    idp.behaviour = 'ok';
  });

  afterAll(async () => {
    await idp.stop();
  });

  it('should_return_authorized_party_as_principal_for_valid_token', async () => {
    await expect(verifier.authenticate(`Bearer ${await idp.token()}`)).resolves.toEqual({
      id: TEST_CLIENT,
    });
  });

  it('should_accept_bearer_scheme_case_insensitively', async () => {
    const token = await idp.token();

    await expect(verifier.authenticate(`bearer ${token}`)).resolves.toEqual({ id: TEST_CLIENT });
    await expect(verifier.authenticate(`BEARER ${token}`)).resolves.toEqual({ id: TEST_CLIENT });
  });

  it('should_reject_token_without_audience_claim', async () => {
    const token = await idp.token({ withoutAudience: true });

    await expectUnauthorized(token, AUTH_MESSAGES.invalidToken);
  });

  it('should_accept_token_whose_audience_list_contains_the_service', async () => {
    const token = await idp.token({ audience: ['account', TEST_AUDIENCE] });

    await expect(verifier.authenticate(`Bearer ${token}`)).resolves.toEqual({ id: TEST_CLIENT });
  });

  it.each([undefined, ''])('should_require_a_token_when_header_is_%p', async (header) => {
    const problem = await problemOf(verifier.authenticate(header));

    expect(problem.code).toBe(ErrorCode.UNAUTHORIZED);
    expect(problem.detail).toBe(AUTH_MESSAGES.missingToken);
  });

  it.each(['Basic abc', 'Bearer not-a-jwt', 'Bearer', 'Token a.b.c'])(
    'should_reject_malformed_authorization_header_%p',
    async (header) => {
      const problem = await problemOf(verifier.authenticate(header));

      expect(problem.code).toBe(ErrorCode.UNAUTHORIZED);
      expect(problem.detail).toBe(AUTH_MESSAGES.malformedToken);
    },
  );

  it.each([
    ['expired', { expired: true }],
    ['foreign issuer', { issuer: 'http://other/realms/x' }],
    ['wrong audience', { audience: 'products-api' }],
    ['missing exp', { withoutExpiration: true }],
    ['future nbf', { notBefore: '10m' }],
    ['unknown kid', { keyId: 'rotated-away' }],
  ])('should_reject_%s_token_as_unauthorized', async (_case, options) => {
    await expectUnauthorized(await idp.token(options), AUTH_MESSAGES.invalidToken);
  });

  it('should_reject_token_signed_by_unknown_key', async () => {
    const token = await idp.token({ key: await TestIdentityProvider.foreignKey() });

    await expectUnauthorized(token, AUTH_MESSAGES.invalidToken);
  });

  it('should_reject_unsigned_alg_none_token', async () => {
    const token = TestIdentityProvider.unsignedToken({
      iss: TEST_ISSUER,
      aud: TEST_AUDIENCE,
      exp: Math.floor(Date.now() / 1000) + 300,
      realm_access: { roles: [TEST_ROLE] },
    });

    await expectUnauthorized(token, AUTH_MESSAGES.invalidToken);
  });

  it('should_reject_hs256_token_signed_with_the_public_key', async () => {
    const token = await new SignJWT({ realm_access: { roles: [TEST_ROLE] } })
      .setProtectedHeader({ alg: 'HS256' })
      .setIssuer(TEST_ISSUER)
      .setAudience(TEST_AUDIENCE)
      .setExpirationTime('5m')
      .sign(new TextEncoder().encode(idp.publicKeyPem));

    await expectUnauthorized(token, AUTH_MESSAGES.invalidToken);
  });

  it('should_reject_token_without_role_as_forbidden', async () => {
    const problem = await problemOf(
      verifier.authenticate(`Bearer ${await idp.token({ roles: ['products-reader'] })}`),
    );

    expect(problem.code).toBe(ErrorCode.FORBIDDEN);
    expect(problem.detail).toBe(AUTH_MESSAGES.missingRole);
  });

  it.each([
    ['non-200 jwks response', 'error' as const, 1000],
    ['jwks timeout', 'hang' as const, 100],
  ])('should_report_identity_provider_unavailable_on_%s', async (_case, behaviour, timeout) => {
    idp.behaviour = behaviour;

    const problem = await problemOf(
      verifierWith({}, timeout).authenticate(`Bearer ${await idp.token()}`),
    );

    expect(problem.code).toBe(ErrorCode.SERVICE_UNAVAILABLE);
    expect(problem.detail).toBe(AUTH_MESSAGES.identityProviderUnavailable);
    expect(problem.cause).toBeInstanceOf(IdentityProviderUnavailableError);
  });

  it('should_rethrow_unknown_failures_so_they_surface_as_internal_errors', async () => {
    const bug = new RangeError('unexpected bug');
    const failingKeys: JWTVerifyGetKey = () => {
      throw bug;
    };

    await expect(
      new JoseAccessTokenVerifier(settings, failingKeys).authenticate(
        `Bearer ${await idp.token()}`,
      ),
    ).rejects.toBe(bug);
  });
});

describe('guardedKeySource', () => {
  it('should_pass_token_errors_through_and_wrap_infrastructure_failures', async () => {
    const tokenError = new errors.JWKSNoMatchingKey();
    const network = new TypeError('fetch failed');
    const header = { alg: 'RS256' };
    const token = { payload: '', signature: '' };

    await expect(guardedKeySource(() => Promise.reject(tokenError))(header, token)).rejects.toBe(
      tokenError,
    );
    await expect(
      guardedKeySource(() => Promise.reject(network))(header, token),
    ).rejects.toMatchObject({ name: 'IdentityProviderUnavailableError', cause: network });
  });
});

describe('token helpers', () => {
  it('should_freeze_verification_policies', () => {
    expect(Object.isFrozen(ACCEPTED_ALGORITHMS)).toBe(true);
    expect(Object.isFrozen(REQUIRED_CLAIMS)).toBe(true);
    expect(ACCEPTED_ALGORITHMS).toEqual(['RS256']);
    expect(REQUIRED_CLAIMS).toEqual(['exp']);
  });

  it('should_extract_token_from_bearer_scheme', () => {
    expect(extractBearerToken('Bearer a.b.c')).toBe('a.b.c');
    expect(extractBearerToken('bearer   a.b.')).toBe('a.b.');
  });

  it.each([
    [{ azp: 'order-processor', sub: 'user-1' }, 'order-processor'],
    [{ sub: 'user-1' }, 'user-1'],
    [{ azp: 42, sub: 'user-1' }, 'user-1'],
  ])('should_derive_principal_from_%j', (payload, id) => {
    expect(principalOf(payload)).toEqual({ id });
  });

  it.each([{}, { sub: '' }])('should_reject_tokens_without_caller_identity_%j', (payload) => {
    expect(() => principalOf(payload)).toThrow(AUTH_MESSAGES.invalidToken);
  });

  it('should_map_verification_errors_by_category', () => {
    const bug = new Error('bug');

    expect(toVerificationError(new errors.JWTExpired('expired', {}))).toMatchObject({
      code: ErrorCode.UNAUTHORIZED,
    });
    expect(toVerificationError(new IdentityProviderUnavailableError(bug))).toMatchObject({
      code: ErrorCode.SERVICE_UNAVAILABLE,
    });
    expect(toVerificationError(bug)).toBe(bug);
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
