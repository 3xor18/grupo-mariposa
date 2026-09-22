import { FaultInjector } from './fault-injector';
import { FaultType } from './fault-rule';

describe('FaultInjector', () => {
  const injector = (): FaultInjector =>
    new FaultInjector([
      { id: 'CLI-1', type: FaultType.SERVICE_UNAVAILABLE, times: 2 },
      { id: 'CLI-2', type: FaultType.TIMEOUT, times: Number.POSITIVE_INFINITY },
    ]);

  it('should_fail_first_n_requests_then_recover_when_times_is_set', () => {
    const faults = injector();

    expect([1, 2, 3, 4].map(() => faults.nextFault('CLI-1'))).toEqual([
      FaultType.SERVICE_UNAVAILABLE,
      FaultType.SERVICE_UNAVAILABLE,
      undefined,
      undefined,
    ]);
  });

  it('should_always_fail_when_times_is_unbounded', () => {
    const faults = injector();

    expect([1, 2, 3].map(() => faults.nextFault('CLI-2'))).toEqual([
      FaultType.TIMEOUT,
      FaultType.TIMEOUT,
      FaultType.TIMEOUT,
    ]);
  });

  it('should_not_inject_when_id_has_no_rule_or_is_missing', () => {
    expect(injector().nextFault('CLI-3')).toBeUndefined();
    expect(injector().nextFault(undefined)).toBeUndefined();
  });

  it('should_count_each_id_independently', () => {
    const faults = injector();
    faults.nextFault('CLI-1');
    faults.nextFault('CLI-1');

    expect(faults.nextFault('CLI-2')).toBe(FaultType.TIMEOUT);
  });
});
