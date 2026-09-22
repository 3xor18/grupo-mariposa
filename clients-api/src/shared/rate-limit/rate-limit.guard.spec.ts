import { ExecutionContext } from '@nestjs/common';
import { Reflector } from '@nestjs/core';
import { testConfig } from '../../../test/support/test-app';
import { AuthenticatedRequest } from '../auth/principal';
import { ErrorCode } from '../errors/error-code.enum';
import { ProblemException } from '../errors/problem.exception';
import { KeyedRateLimiter } from './keyed-rate-limiter';
import {
  ClientAddressRateLimitGuard,
  PrincipalRateLimitGuard,
  RATE_LIMITED_DETAIL,
  UNKNOWN_CLIENT_ADDRESS,
} from './rate-limit.guard';
import { createRateLimiter } from './rate-limit.module';

const SETTINGS = { requestsPerSecond: 0.5, burst: 1, maxTrackedCallers: 10 };

function contextFor(request: Partial<AuthenticatedRequest>): ExecutionContext {
  return {
    getHandler: () => undefined,
    getClass: () => undefined,
    switchToHttp: () => ({ getRequest: () => request }),
  } as unknown as ExecutionContext;
}

function reflectorReturning(exempt: boolean | undefined): Reflector {
  return { getAllAndOverride: jest.fn().mockReturnValue(exempt) } as unknown as Reflector;
}

function rejectionOf(action: () => unknown): ProblemException {
  try {
    action();
  } catch (error: unknown) {
    if (error instanceof ProblemException) {
      return error;
    }
  }
  throw new Error('expected a ProblemException');
}

describe('ClientAddressRateLimitGuard', () => {
  const limiter = (): KeyedRateLimiter => new KeyedRateLimiter(SETTINGS, () => 0);

  it('should_limit_each_client_address_independently', () => {
    const guard = new ClientAddressRateLimitGuard(reflectorReturning(undefined), limiter());
    const first = contextFor({ ip: '10.0.0.1' });

    expect(guard.canActivate(first)).toBe(true);
    expect(guard.canActivate(contextFor({ ip: '10.0.0.2' }))).toBe(true);
    const problem = rejectionOf(() => guard.canActivate(first));

    expect(problem.code).toBe(ErrorCode.RATE_LIMITED);
    expect(problem.detail).toBe(RATE_LIMITED_DETAIL);
    expect(problem.options.headers).toEqual({ 'Retry-After': '2' });
  });

  it('should_share_one_bucket_for_requests_without_address', () => {
    const shared = limiter();
    const guard = new ClientAddressRateLimitGuard(reflectorReturning(false), shared);
    guard.canActivate(contextFor({}));

    expect(shared.tryTake(UNKNOWN_CLIENT_ADDRESS).allowed).toBe(false);
  });

  it('should_not_consume_tokens_when_route_is_exempt', () => {
    const shared = limiter();
    const guard = new ClientAddressRateLimitGuard(reflectorReturning(true), shared);

    expect([1, 2, 3].map(() => guard.canActivate(contextFor({ ip: '10.0.0.1' })))).toEqual([
      true,
      true,
      true,
    ]);
    expect(shared.trackedCallers).toBe(0);
  });
});

describe('PrincipalRateLimitGuard', () => {
  it('should_limit_each_authenticated_principal_independently', () => {
    const guard = new PrincipalRateLimitGuard(
      reflectorReturning(undefined),
      new KeyedRateLimiter(SETTINGS, () => 0),
    );
    const orderProcessor = contextFor({ principal: { id: 'order-processor' } });

    expect(guard.canActivate(orderProcessor)).toBe(true);
    expect(guard.canActivate(contextFor({ principal: { id: 'reporting' } }))).toBe(true);
    expect(rejectionOf(() => guard.canActivate(orderProcessor)).code).toBe(ErrorCode.RATE_LIMITED);
  });

  it('should_skip_requests_without_principal', () => {
    const shared = new KeyedRateLimiter(SETTINGS, () => 0);
    const guard = new PrincipalRateLimitGuard(reflectorReturning(undefined), shared);

    expect(guard.canActivate(contextFor({}))).toBe(true);
    expect(guard.canActivate(contextFor({}))).toBe(true);
    expect(shared.trackedCallers).toBe(0);
  });
});

describe('createRateLimiter', () => {
  it('should_build_keyed_limiter_from_configuration', () => {
    const limiter = createRateLimiter(
      testConfig('http://unused', {
        rateLimit: { requestsPerSecond: 1, burst: 2, maxTrackedCallers: 5 },
      }),
    );

    expect([1, 2, 3].map(() => limiter.tryTake('caller').allowed)).toEqual([true, true, false]);
  });
});
