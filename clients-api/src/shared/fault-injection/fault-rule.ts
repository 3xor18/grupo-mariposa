export enum FaultType {
  BAD_REQUEST = '400',
  TOO_MANY_REQUESTS = '429',
  INTERNAL_ERROR = '500',
  BAD_GATEWAY = '502',
  SERVICE_UNAVAILABLE = '503',
  TIMEOUT = 'timeout',
}

export interface FaultRule {
  readonly id: string;
  readonly type: FaultType;
  readonly times: number;
}

export class FaultRuleSyntaxError extends Error {
  constructor(entry: string) {
    super(`Invalid fault rule "${entry}", expected id:type[:times]`);
    this.name = FaultRuleSyntaxError.name;
  }
}

const RULE_SEPARATOR = ',';
const PART_SEPARATOR = ':';
const TIMES_PATTERN = /^[1-9]\d*$/;
const FAULT_TYPES: ReadonlySet<string> = new Set(Object.values(FaultType));

function isFaultType(value: string): value is FaultType {
  return FAULT_TYPES.has(value);
}

function parseTimes(raw: string | undefined, entry: string): number {
  if (raw === undefined) {
    return Number.POSITIVE_INFINITY;
  }
  if (!TIMES_PATTERN.test(raw)) {
    throw new FaultRuleSyntaxError(entry);
  }
  return Number(raw);
}

function parseRule(entry: string): FaultRule {
  const [id, type, times, ...rest] = entry.split(PART_SEPARATOR);
  if (!id || type === undefined || !isFaultType(type) || rest.length > 0) {
    throw new FaultRuleSyntaxError(entry);
  }
  return { id, type, times: parseTimes(times, entry) };
}

export function parseFaultRules(raw: string): readonly FaultRule[] {
  return raw
    .split(RULE_SEPARATOR)
    .map((entry) => entry.trim())
    .filter((entry) => entry.length > 0)
    .map(parseRule);
}
