import { ErrorCode } from '../errors/error-code.enum';
import { faultProblem, INJECTED_FAULT_DETAIL } from './fault-problem';
import { FaultType } from './fault-rule';

describe('faultProblem', () => {
  it.each([
    [FaultType.BAD_REQUEST, ErrorCode.VALIDATION_ERROR],
    [FaultType.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR],
    [FaultType.BAD_GATEWAY, ErrorCode.BAD_GATEWAY],
    [FaultType.SERVICE_UNAVAILABLE, ErrorCode.SERVICE_UNAVAILABLE],
  ] as const)('should_map_fault_%s_to_%s_without_headers', (fault, code) => {
    const problem = faultProblem(fault);

    expect(problem.code).toBe(code);
    expect(problem.detail).toBe(INJECTED_FAULT_DETAIL);
    expect(problem.options).toEqual({});
  });

  it('should_add_retry_after_when_fault_is_429', () => {
    const problem = faultProblem(FaultType.TOO_MANY_REQUESTS);

    expect(problem.code).toBe(ErrorCode.RATE_LIMITED);
    expect(problem.options.headers).toEqual({ 'Retry-After': '1' });
  });
});
