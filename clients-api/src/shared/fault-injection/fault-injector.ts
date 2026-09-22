import { FaultRule, FaultType } from './fault-rule';

export const FAULT_INJECTOR = Symbol('FAULT_INJECTOR');

export class FaultInjector {
  private readonly rules: ReadonlyMap<string, FaultRule>;
  private readonly injected = new Map<string, number>();

  constructor(rules: readonly FaultRule[]) {
    this.rules = new Map(rules.map((rule) => [rule.id, rule]));
  }

  nextFault(id: string | undefined): FaultType | undefined {
    const rule = id === undefined ? undefined : this.rules.get(id);
    if (rule === undefined) {
      return undefined;
    }
    const alreadyInjected = this.injected.get(rule.id) ?? 0;
    if (alreadyInjected >= rule.times) {
      return undefined;
    }
    this.injected.set(rule.id, alreadyInjected + 1);
    return rule.type;
  }
}
