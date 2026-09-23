import { CallHandler, ExecutionContext } from '@nestjs/common';
import { Reflector } from '@nestjs/core';
import { EventEmitter } from 'node:events';
import { lastValueFrom, of } from 'rxjs';
import { testConfig } from '../../../test/support/test-app';
import { FaultInjectionConfig } from '../../config/app-config';
import { ErrorCode } from '../errors/error-code.enum';
import { ProblemException } from '../errors/problem.exception';
import { RequestAbortedError } from '../errors/request-aborted.error';
import { SHUTTING_DOWN_DETAIL, FaultInjectionInterceptor } from './fault-injection.interceptor';
import { createFaultInjector, faultInjectionConfigOf } from './fault-injection.module';
import { FaultInjector } from './fault-injector';
import { FaultType, parseFaultRules } from './fault-rule';
import { PendingHolds } from './pending-holds';

const CONFIG: FaultInjectionConfig = {
  enabled: true,
  rules: parseFaultRules('CLI-1:503,CLI-2:timeout'),
  timeoutMs: 20,
};

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

  function interceptor(
    keyParam: string | undefined,
    holds = new PendingHolds(),
  ): FaultInjectionInterceptor {
    const reflector = { get: jest.fn().mockReturnValue(keyParam) } as unknown as Reflector;
    return new FaultInjectionInterceptor(reflector, createFaultInjector(CONFIG), CONFIG, holds);
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
    ).rejects.toMatchObject({ code: ErrorCode.SERVICE_UNAVAILABLE, detail: 'Injected fault' });
  });

  it('should_hold_then_continue_when_rule_injects_timeout', async () => {
    const startedAt = Date.now();
    const result = await interceptor('clientId').intercept(contextFor({ clientId: 'CLI-2' }), next);

    await expect(lastValueFrom(result)).resolves.toBe('handled');
    expect(Date.now() - startedAt).toBeGreaterThanOrEqual(CONFIG.timeoutMs - 1);
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

  it('should_answer_503_when_shutdown_cancels_the_hold', async () => {
    const holds = new PendingHolds();
    const pending = interceptor('clientId', holds).intercept(
      contextFor({ clientId: 'CLI-2' }),
      next,
    );
    holds.cancelAll();

    await expect(pending).rejects.toEqual(
      new ProblemException(ErrorCode.SERVICE_UNAVAILABLE, SHUTTING_DOWN_DETAIL),
    );
  });
});

describe('fault injection factories', () => {
  it('should_expose_only_the_fault_injection_section', () => {
    const config = testConfig('http://unused');

    expect(faultInjectionConfigOf(config)).toBe(config.faultInjection);
  });

  it('should_ignore_rules_when_fault_injection_is_disabled', () => {
    const disabled: FaultInjector = createFaultInjector({ ...CONFIG, enabled: false });
    const enabled: FaultInjector = createFaultInjector(CONFIG);

    expect(disabled.nextFault('CLI-1')).toBeUndefined();
    expect(enabled.nextFault('CLI-1')).toBe(FaultType.SERVICE_UNAVAILABLE);
  });
});
