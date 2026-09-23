import { ExecutionContext } from '@nestjs/common';
import { Reflector } from '@nestjs/core';
import { NO_AUTH, testConfig } from '../../../test/support/test-app';
import { DisabledAccessTokenVerifier } from './access-token-verifier';
import { createAccessTokenVerifier } from './auth.module';
import { JoseAccessTokenVerifier } from './jose-access-token-verifier';
import { JwtAuthGuard } from './jwt-auth.guard';
import { AccessRole, REQUIRED_ACCESS_ROLE_KEY } from './access-role';
import { IS_PUBLIC_KEY } from './public.decorator';
import { AuthenticatedRequest, Principal } from './principal';

function contextFor(request: Partial<AuthenticatedRequest>): ExecutionContext {
  return {
    getHandler: () => undefined,
    getClass: () => undefined,
    switchToHttp: () => ({ getRequest: () => request }),
  } as unknown as ExecutionContext;
}

describe('JwtAuthGuard', () => {
  const verifier = {
    authenticate: jest.fn<Promise<Principal | undefined>, [string | undefined, AccessRole?]>(),
  };

  function guard(isPublic: boolean | undefined, role?: AccessRole): JwtAuthGuard {
    const metadata: Record<string, unknown> = {
      [IS_PUBLIC_KEY]: isPublic,
      [REQUIRED_ACCESS_ROLE_KEY]: role,
    };
    const reflector = { getAllAndOverride: jest.fn((key: string) => metadata[key]) };
    return new JwtAuthGuard(reflector as unknown as Reflector, verifier);
  }

  beforeEach(() => {
    verifier.authenticate.mockReset();
  });

  it('should_skip_verification_when_route_is_public', async () => {
    const request: Partial<AuthenticatedRequest> = { headers: {} };

    await expect(guard(true).canActivate(contextFor(request))).resolves.toBe(true);
    expect(verifier.authenticate).not.toHaveBeenCalled();
    expect(request.principal).toBeUndefined();
  });

  it('should_attach_principal_when_route_is_protected', async () => {
    verifier.authenticate.mockResolvedValueOnce({ id: 'order-processor' });
    const request: Partial<AuthenticatedRequest> = { headers: { authorization: 'Bearer x.y.z' } };

    await expect(guard(undefined).canActivate(contextFor(request))).resolves.toBe(true);
    expect(verifier.authenticate).toHaveBeenCalledWith('Bearer x.y.z', AccessRole.READER);
    expect(request.principal).toEqual({ id: 'order-processor' });
  });

  it('should_request_the_admin_role_when_route_requires_it', async () => {
    verifier.authenticate.mockResolvedValueOnce({ id: 'backoffice' });
    const request: Partial<AuthenticatedRequest> = { headers: { authorization: 'Bearer a.b.c' } };

    await expect(guard(false, AccessRole.ADMIN).canActivate(contextFor(request))).resolves.toBe(
      true,
    );
    expect(verifier.authenticate).toHaveBeenCalledWith('Bearer a.b.c', AccessRole.ADMIN);
  });

  it('should_leave_principal_empty_when_authentication_is_disabled', async () => {
    verifier.authenticate.mockResolvedValueOnce(undefined);
    const request: Partial<AuthenticatedRequest> = { headers: {} };

    await expect(guard(false).canActivate(contextFor(request))).resolves.toBe(true);
    expect(request.principal).toBeUndefined();
  });

  it('should_propagate_rejection_when_verifier_fails', async () => {
    verifier.authenticate.mockRejectedValueOnce(new Error('denied'));

    await expect(guard(false).canActivate(contextFor({ headers: {} }))).rejects.toThrow('denied');
  });
});

describe('createAccessTokenVerifier', () => {
  it('should_create_noop_verifier_when_auth_is_disabled', async () => {
    const verifier = createAccessTokenVerifier({ ...testConfig('http://j/certs'), auth: NO_AUTH });

    expect(verifier).toBeInstanceOf(DisabledAccessTokenVerifier);
    await expect(verifier.authenticate(undefined)).resolves.toBeUndefined();
  });

  it('should_create_jose_verifier_when_auth_is_enabled', () => {
    expect(createAccessTokenVerifier(testConfig('http://j/certs'))).toBeInstanceOf(
      JoseAccessTokenVerifier,
    );
  });
});
