import { CanActivate, ExecutionContext, Inject, Injectable } from '@nestjs/common';
import { Reflector } from '@nestjs/core';
import { AuthenticatedRequest } from '../auth/principal';
import { HTTP_HEADERS } from '../constants/http.constants';
import { ERROR_CATALOG } from '../errors/error-catalog';
import { ErrorCode } from '../errors/error-code.enum';
import { ProblemException } from '../errors/problem.exception';
import { KeyedRateLimiter } from './keyed-rate-limiter';
import { SKIP_RATE_LIMIT_KEY } from './skip-rate-limit.decorator';

export const CLIENT_ADDRESS_RATE_LIMITER = Symbol('CLIENT_ADDRESS_RATE_LIMITER');
export const PRINCIPAL_RATE_LIMITER = Symbol('PRINCIPAL_RATE_LIMITER');
export const RATE_LIMITED_DETAIL = ERROR_CATALOG[ErrorCode.RATE_LIMITED].detail;
export const UNKNOWN_CLIENT_ADDRESS = 'unknown';

abstract class CallerRateLimitGuard implements CanActivate {
  protected constructor(
    private readonly reflector: Reflector,
    private readonly limiter: KeyedRateLimiter,
  ) {}

  canActivate(context: ExecutionContext): boolean {
    const key = this.callerKeyOf(context.switchToHttp().getRequest<AuthenticatedRequest>());
    if (key === undefined || this.isExempt(context)) {
      return true;
    }
    const decision = this.limiter.tryTake(key);
    if (!decision.allowed) {
      throw new ProblemException(ErrorCode.RATE_LIMITED, RATE_LIMITED_DETAIL, {
        headers: { [HTTP_HEADERS.RETRY_AFTER]: String(decision.retryAfterSeconds) },
      });
    }
    return true;
  }

  protected abstract callerKeyOf(request: AuthenticatedRequest): string | undefined;

  private isExempt(context: ExecutionContext): boolean {
    return (
      this.reflector.getAllAndOverride<boolean | undefined>(SKIP_RATE_LIMIT_KEY, [
        context.getHandler(),
        context.getClass(),
      ]) === true
    );
  }
}

@Injectable()
export class ClientAddressRateLimitGuard extends CallerRateLimitGuard {
  constructor(
    reflector: Reflector,
    @Inject(CLIENT_ADDRESS_RATE_LIMITER) limiter: KeyedRateLimiter,
  ) {
    super(reflector, limiter);
  }

  protected override callerKeyOf(request: AuthenticatedRequest): string {
    return request.ip ?? UNKNOWN_CLIENT_ADDRESS;
  }
}

@Injectable()
export class PrincipalRateLimitGuard extends CallerRateLimitGuard {
  constructor(reflector: Reflector, @Inject(PRINCIPAL_RATE_LIMITER) limiter: KeyedRateLimiter) {
    super(reflector, limiter);
  }

  protected override callerKeyOf(request: AuthenticatedRequest): string | undefined {
    return request.principal?.id;
  }
}
