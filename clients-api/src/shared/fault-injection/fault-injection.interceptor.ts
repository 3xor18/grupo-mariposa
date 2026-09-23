import { CallHandler, ExecutionContext, Inject, Injectable, NestInterceptor } from '@nestjs/common';
import { Reflector } from '@nestjs/core';
import { Request, Response } from 'express';
import { Observable } from 'rxjs';
import { FaultInjectionConfig } from '../../config/app-config';
import { ErrorCode } from '../errors/error-code.enum';
import { ProblemException } from '../errors/problem.exception';
import { RequestAbortedError } from '../errors/request-aborted.error';
import { FAULT_INJECTION_KEY } from './fault-injection-key.decorator';
import { FAULT_INJECTION_CONFIG, FAULT_INJECTOR, PENDING_HOLDS } from './fault-injection.tokens';
import { FaultInjector } from './fault-injector';
import { faultProblem } from './fault-problem';
import { FaultType } from './fault-rule';
import { holdResponse, HoldOutcome } from './hold-response';
import { PendingHolds } from './pending-holds';

export const SHUTTING_DOWN_DETAIL = 'The service is shutting down';

function routeParam(request: Request, name: string): string | undefined {
  const value = request.params[name];
  return typeof value === 'string' ? value : undefined;
}

@Injectable()
export class FaultInjectionInterceptor implements NestInterceptor {
  constructor(
    private readonly reflector: Reflector,
    @Inject(FAULT_INJECTOR) private readonly injector: FaultInjector,
    @Inject(FAULT_INJECTION_CONFIG) private readonly config: FaultInjectionConfig,
    @Inject(PENDING_HOLDS) private readonly holds: PendingHolds,
  ) {}

  async intercept(context: ExecutionContext, next: CallHandler): Promise<Observable<unknown>> {
    const keyParam = this.reflector.get<string | undefined>(
      FAULT_INJECTION_KEY,
      context.getHandler(),
    );
    if (keyParam === undefined) {
      return next.handle();
    }
    const http = context.switchToHttp();
    const fault = this.injector.nextFault(routeParam(http.getRequest<Request>(), keyParam));
    if (fault === FaultType.TIMEOUT) {
      await this.hold(http.getResponse<Response>());
    } else if (fault !== undefined) {
      throw faultProblem(fault);
    }
    return next.handle();
  }

  private async hold(response: Response): Promise<void> {
    const outcome = await holdResponse(response, this.config.timeoutMs, this.holds.signal);
    if (outcome === HoldOutcome.ABORTED) {
      throw new RequestAbortedError();
    }
    if (outcome === HoldOutcome.CANCELLED) {
      throw new ProblemException(ErrorCode.SERVICE_UNAVAILABLE, SHUTTING_DOWN_DETAIL);
    }
  }
}
