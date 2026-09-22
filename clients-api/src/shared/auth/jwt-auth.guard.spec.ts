import { ExecutionContext } from '@nestjs/common';
import { Reflector } from '@nestjs/core';
import { LogLevel } from '../../config/app-config';
import { DisabledAccessTokenVerifier } from './access-token-verifier';
import { createAccessTokenVerifier } from './auth.module';
import { JoseAccessTokenVerifier } from './jose-access-token-verifier';
import { JwtAuthGuard } from './jwt-auth.guard';

function contextWith(authorization?: string): ExecutionContext {
  return {
    getHandler: () => undefined,
    getClass: () => undefined,
    switchToHttp: () => ({ getRequest: () => ({ headers: { authorization } }) }),
  } as unknown as ExecutionContext;
}

describe('JwtAuthGuard', () => {
  const verifier = { authenticate: jest.fn().mockResolvedValue(undefined) };

  function guard(isPublic: boolean | undefined): JwtAuthGuard {
    const reflector = { getAllAndOverride: jest.fn().mockReturnValue(isPublic) };
    return new JwtAuthGuard(reflector as unknown as Reflector, verifier);
  }

  beforeEach(() => {
    verifier.authenticate.mockClear();
  });

  it('should_skip_verification_when_route_is_public', async () => {
    await expect(guard(true).canActivate(contextWith())).resolves.toBe(true);
    expect(verifier.authenticate).not.toHaveBeenCalled();
  });

  it('should_verify_authorization_header_when_route_is_protected', async () => {
    await expect(guard(undefined).canActivate(contextWith('Bearer x.y.z'))).resolves.toBe(true);
    expect(verifier.authenticate).toHaveBeenCalledWith('Bearer x.y.z');
  });

  it('should_propagate_rejection_when_verifier_fails', async () => {
    verifier.authenticate.mockRejectedValueOnce(new Error('denied'));

    await expect(guard(false).canActivate(contextWith())).rejects.toThrow('denied');
  });
});

describe('createAccessTokenVerifier', () => {
  const base = {
    port: 0,
    logLevel: LogLevel.SILENT,
    faultInjection: { rules: [], timeoutMs: 1 },
    rateLimit: { requestsPerSecond: 1, burst: 1 },
  };

  it('should_create_noop_verifier_when_auth_is_disabled', async () => {
    const verifier = createAccessTokenVerifier({ ...base, auth: { enabled: false } });

    expect(verifier).toBeInstanceOf(DisabledAccessTokenVerifier);
    await expect(verifier.authenticate(undefined)).resolves.toBeUndefined();
  });

  it('should_create_jose_verifier_when_auth_is_enabled', () => {
    const verifier = createAccessTokenVerifier({
      ...base,
      auth: { enabled: true, issuer: 'http://i', jwksUrl: 'http://j/certs', requiredRole: 'r' },
    });

    expect(verifier).toBeInstanceOf(JoseAccessTokenVerifier);
  });
});
