import { systemClock, TokenBucket } from './token-bucket';

describe('TokenBucket', () => {
  let now: number;
  const clock = (): number => now;

  beforeEach(() => {
    now = 0;
  });

  it('should_allow_burst_then_reject_when_bucket_is_empty', () => {
    const bucket = new TokenBucket(3, 1, clock);

    const decisions = [1, 2, 3, 4].map(() => bucket.tryTake().allowed);

    expect(decisions).toEqual([true, true, true, false]);
  });

  it('should_refill_at_configured_rate_without_exceeding_capacity', () => {
    const bucket = new TokenBucket(2, 10, clock);
    bucket.tryTake();
    bucket.tryTake();

    now = 100;
    expect(bucket.tryTake().allowed).toBe(true);
    expect(bucket.tryTake().allowed).toBe(false);

    now = 60_000;
    expect([1, 2, 3].map(() => bucket.tryTake().allowed)).toEqual([true, true, false]);
  });

  it('should_report_seconds_until_next_token_when_rejecting', () => {
    const bucket = new TokenBucket(1, 0.25, clock);
    bucket.tryTake();

    expect(bucket.tryTake()).toEqual({ allowed: false, retryAfterSeconds: 4 });
  });

  it('should_report_at_least_one_second_when_refill_is_fast', () => {
    const bucket = new TokenBucket(1, 1000, clock);
    bucket.tryTake();

    expect(bucket.tryTake()).toEqual({ allowed: false, retryAfterSeconds: 1 });
  });

  it('should_use_monotonic_system_clock_by_default', () => {
    expect(systemClock()).toBeGreaterThanOrEqual(0);
    expect(new TokenBucket(1, 1).tryTake().allowed).toBe(true);
  });
});
