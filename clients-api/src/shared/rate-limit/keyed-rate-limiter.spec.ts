import { KeyedRateLimiter } from './keyed-rate-limiter';

describe('KeyedRateLimiter', () => {
  const settings = { requestsPerSecond: 1, burst: 1, maxTrackedCallers: 2 };

  it('should_keep_an_independent_bucket_per_key', () => {
    const limiter = new KeyedRateLimiter(settings, () => 0);

    expect(limiter.tryTake('a').allowed).toBe(true);
    expect(limiter.tryTake('b').allowed).toBe(true);
    expect(limiter.tryTake('a')).toEqual({ allowed: false, retryAfterSeconds: 1 });
    expect(limiter.trackedCallers).toBe(2);
  });

  it('should_evict_least_recently_used_key_when_capacity_is_reached', () => {
    const limiter = new KeyedRateLimiter(settings, () => 0);
    limiter.tryTake('a');
    limiter.tryTake('b');
    limiter.tryTake('a');

    limiter.tryTake('c');

    expect(limiter.trackedCallers).toBe(2);
    expect(limiter.tryTake('a').allowed).toBe(false);
    expect(limiter.tryTake('b').allowed).toBe(true);
  });

  it('should_use_system_clock_by_default', () => {
    expect(new KeyedRateLimiter(settings).tryTake('a').allowed).toBe(true);
  });
});
