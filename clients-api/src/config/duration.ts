const DURATION_PATTERN = /^[1-9]\d*[smhd]$/;
const SECONDS_PER_UNIT = Object.freeze({ s: 1, m: 60, h: 3600, d: 86_400 });

type DurationUnit = keyof typeof SECONDS_PER_UNIT;

export const DURATION_FORMAT_MESSAGE = 'must be a positive duration such as 30m, 12h or 7d';

function isDurationUnit(unit: string): unit is DurationUnit {
  return Object.hasOwn(SECONDS_PER_UNIT, unit);
}

export function parseDurationSeconds(raw: string): number | undefined {
  const trimmed = raw.trim();
  const unit = trimmed.slice(-1);
  if (!DURATION_PATTERN.test(trimmed) || !isDurationUnit(unit)) {
    return undefined;
  }
  return Number(trimmed.slice(0, -1)) * SECONDS_PER_UNIT[unit];
}
