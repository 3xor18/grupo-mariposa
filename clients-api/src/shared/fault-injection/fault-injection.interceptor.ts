import { CallHandler, ExecutionContext, Inject, Injectable, NestInterceptor } from '@nestjs/common';
import { Reflector } from '@nestjs/core';
import { Request, Response } from 'express';
import { Observable } from 'rxjs';
import { APP_CONFIG, AppConfig } from '../../config/app-config';
import { RequestAbortedError } from '../errors/request-aborted.error';
import { faultProblem } from './fault-problem';
import { FaultType } from './fault-rule';
import { FAULT_INJECTION_KEY } from './fault-injection-key.decorator';
import { FAULT_INJECTOR, FaultInjector } from './fault-injector';
import { holdResponse, HoldOutcome } from './hold-response';

function routeParam(request: Request, name: string): string | undefined {
  const value = request.params[name];
  return typeof value === 'string' ? value : undefined;
}

@Injectable()
export class FaultInjectionInterceptor implements NestInterceptor {
  constructor(
    private readonly reflector: Reflector,
    @Inject(FAULT_INJECTOR) private readonly injector: FaultInjector,
    @Inject(APP_CONFIG) private readonly config: AppConfig,
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
      await this.holdOrAbort(http.getResponse<Response>());
    } else if (fault !== undefined) {
      throw faultProblem(fault);
    }
    return next.handle();
  }

  private async holdOrAbort(response: Response): Promise<void> {
    const outcome = await holdResponse(response, this.config.faultInjection.timeoutMs);
    if (outcome === HoldOutcome.ABORTED) {
      throw new RequestAbortedError();
    }
  }
}
