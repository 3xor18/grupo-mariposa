import { ExecutionContext } from '@nestjs/common';
import { Reflector } from '@nestjs/core';
import { LogLevel } from '../../config/app-config';
import { ErrorCode } from '../errors/error-code.enum';
import { ProblemException } from '../errors/problem.exception';
import { createRateLimiter } from './rate-limit.module';
import { RateLimitGuard } from './rate-limit.guard';
import { TokenBucket } from './token-bucket';

const context = {
  getHandler: () => undefined,
  getClass: () => undefined,
} as unknown as ExecutionContext;

function guard(exempt: boolean | undefined, bucket: TokenBucket): RateLimitGuard {
  const reflector = { getAllAndOverride: jest.fn().mockReturnValue(exempt) };
  return new RateLimitGuard(reflector as unknown as Reflector, bucket);
}

describe('RateLimitGuard', () => {
  it('should_allow_while_tokens_remain', () => {
    expect(guard(undefined, new TokenBucket(1, 1, () => 0)).canActivate(context)).toBe(true);
  });

  it('should_throw_rate_limited_problem_with_retry_after_when_exhausted', () => {
    const target = guard(false, new TokenBucket(1, 0.5, () => 0));
    target.canActivate(context);

    const attempt = (): boolean => target.canActivate(context);

    expect(attempt).toThrow(ProblemException);
    try {
      attempt();
    } catch (error: unknown) {
      expect(error).toMatchObject({
        code: ErrorCode.RATE_LIMITED,
        options: { headers: { 'Retry-After': '2' } },
      });
    }
  });

  it('should_not_consume_tokens_when_route_is_exempt', () => {
    const bucket = new TokenBucket(1, 1, () => 0);
    const target = guard(true, bucket);

    expect([1, 2, 3].map(() => target.canActivate(context))).toEqual([true, true, true]);
    expect(bucket.tryTake().allowed).toBe(true);
  });
});

describe('createRateLimiter', () => {
  it('should_build_bucket_from_configuration', () => {
    const bucket = createRateLimiter({
      port: 0,
      logLevel: LogLevel.SILENT,
      auth: { enabled: false },
      faultInjection: { rules: [], timeoutMs: 1 },
      rateLimit: { requestsPerSecond: 1, burst: 2 },
    });

    expect([1, 2, 3].map(() => bucket.tryTake().allowed)).toEqual([true, true, false]);
  });
});
