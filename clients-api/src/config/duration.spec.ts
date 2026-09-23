import { parseDurationSeconds } from './duration';

describe('parseDurationSeconds', () => {
  it.each([
    ['45s', 45],
    ['30m', 1800],
    ['12h', 43_200],
    [' 7d ', 604_800],
  ])('should_parse_%p', (raw, seconds) => {
    expect(parseDurationSeconds(raw)).toBe(seconds);
  });

  it.each(['', '7', 'd', '0d', '-1d', '7w', '1.5h', '7 d'])('should_reject_%p', (raw) => {
    expect(parseDurationSeconds(raw)).toBeUndefined();
  });
});
