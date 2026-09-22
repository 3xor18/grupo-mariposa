import { CanActivate, ExecutionContext, Inject, Injectable } from '@nestjs/common';
import { Reflector } from '@nestjs/core';
import { ErrorCode } from '../errors/error-code.enum';
import { ProblemException } from '../errors/problem.exception';
import { SKIP_RATE_LIMIT_KEY } from './skip-rate-limit.decorator';
import { TokenBucket } from './token-bucket';

export const RATE_LIMITER = Symbol('RATE_LIMITER');
export const RETRY_AFTER_HEADER = 'Retry-After';
export const RATE_LIMITED_DETAIL = 'Request rate limit exceeded, retry later';

@Injectable()
export class RateLimitGuard implements CanActivate {
  constructor(
    private readonly reflector: Reflector,
    @Inject(RATE_LIMITER) private readonly bucket: TokenBucket,
  ) {}

  canActivate(context: ExecutionContext): boolean {
    if (this.isExempt(context)) {
      return true;
    }
    const decision = this.bucket.tryTake();
    if (!decision.allowed) {
      throw new ProblemException(ErrorCode.RATE_LIMITED, RATE_LIMITED_DETAIL, {
        headers: { [RETRY_AFTER_HEADER]: String(decision.retryAfterSeconds) },
      });
    }
    return true;
  }

  private isExempt(context: ExecutionContext): boolean {
    return (
      this.reflector.getAllAndOverride<boolean | undefined>(SKIP_RATE_LIMIT_KEY, [
        context.getHandler(),
        context.getClass(),
      ]) === true
    );
  }
}
