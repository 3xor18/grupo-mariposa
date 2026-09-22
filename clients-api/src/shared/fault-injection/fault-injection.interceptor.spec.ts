import { CallHandler, ExecutionContext } from '@nestjs/common';
import { Reflector } from '@nestjs/core';
import { EventEmitter } from 'node:events';
import { lastValueFrom, of } from 'rxjs';
import { AppConfig } from '../../config/app-config';
import { ProblemException } from '../errors/problem.exception';
import { RequestAbortedError } from '../errors/request-aborted.error';
import { FaultInjectionInterceptor } from './fault-injection.interceptor';
import { FaultInjector } from './fault-injector';
import { parseFaultRules } from './fault-rule';

const config = { faultInjection: { rules: [], timeoutMs: 20 } } as unknown as AppConfig;

function contextFor(
  params: Record<string, unknown>,
  response = new EventEmitter(),
): ExecutionContext {
  return {
    getHandler: () => undefined,
    switchToHttp: () => ({ getRequest: () => ({ params }), getResponse: () => response }),
  } as unknown as ExecutionContext;
}

describe('FaultInjectionInterceptor', () => {
  const next: CallHandler = { handle: () => of('handled') };

  function interceptor(keyParam: string | undefined): FaultInjectionInterceptor {
    const reflector = { get: jest.fn().mockReturnValue(keyParam) } as unknown as Reflector;
    const injector = new FaultInjector(parseFaultRules('CLI-1:503,CLI-2:timeout'));
    return new FaultInjectionInterceptor(reflector, injector, config);
  }

  it('should_pass_through_when_handler_has_no_fault_key', async () => {
    const result = await interceptor(undefined).intercept(contextFor({}), next);

    await expect(lastValueFrom(result)).resolves.toBe('handled');
  });

  it('should_pass_through_when_param_has_no_rule_or_is_not_a_string', async () => {
    const target = interceptor('clientId');

    await expect(
      lastValueFrom(await target.intercept(contextFor({ clientId: 'CLI-9' }), next)),
    ).resolves.toBe('handled');
    await expect(
      lastValueFrom(await target.intercept(contextFor({ clientId: ['CLI-1'] }), next)),
    ).resolves.toBe('handled');
  });

  it('should_throw_problem_when_rule_injects_failure', async () => {
    await expect(
      interceptor('clientId').intercept(contextFor({ clientId: 'CLI-1' }), next),
    ).rejects.toBeInstanceOf(ProblemException);
  });

  it('should_hold_then_continue_when_rule_injects_timeout', async () => {
    const result = await interceptor('clientId').intercept(contextFor({ clientId: 'CLI-2' }), next);

    await expect(lastValueFrom(result)).resolves.toBe('handled');
  });

  it('should_abort_when_client_disconnects_during_timeout', async () => {
    const response = new EventEmitter();
    const pending = interceptor('clientId').intercept(
      contextFor({ clientId: 'CLI-2' }, response),
      next,
    );
    response.emit('close');

    await expect(pending).rejects.toBeInstanceOf(RequestAbortedError);
  });
});
