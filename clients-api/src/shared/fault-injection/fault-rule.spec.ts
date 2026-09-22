import { FaultRuleSyntaxError, FaultType, parseFaultRules } from './fault-rule';

describe('parseFaultRules', () => {
  it('should_return_no_rules_when_value_is_blank', () => {
    expect(parseFaultRules('')).toEqual([]);
    expect(parseFaultRules(' , ')).toEqual([]);
  });

  it('should_parse_platform_example_when_rules_use_every_shape', () => {
    expect(parseFaultRules('PRD-012:503:2,PRD-013:timeout,PRD-014:400')).toEqual([
      { id: 'PRD-012', type: FaultType.SERVICE_UNAVAILABLE, times: 2 },
      { id: 'PRD-013', type: FaultType.TIMEOUT, times: Number.POSITIVE_INFINITY },
      { id: 'PRD-014', type: FaultType.BAD_REQUEST, times: Number.POSITIVE_INFINITY },
    ]);
  });

  it.each(['429', '500', '502'])('should_accept_fault_type_%s', (type) => {
    expect(parseFaultRules(`CLI-1:${type}`)[0]?.type).toBe(type);
  });

  it.each(['CLI-1', ':503', 'CLI-1:404', 'CLI-1:503:0', 'CLI-1:503:x', 'CLI-1:503:1:2'])(
    'should_reject_malformed_rule_%s',
    (entry) => {
      expect(() => parseFaultRules(entry)).toThrow(FaultRuleSyntaxError);
      expect(() => parseFaultRules(entry)).toThrow(`Invalid fault rule "${entry}"`);
    },
  );
});
