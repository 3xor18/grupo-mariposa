import { RateLimitConfig } from '../../config/app-config';
import { MonotonicClock, systemClock, TakeDecision, TokenBucket } from './token-bucket';

export class KeyedRateLimiter {
  private readonly buckets = new Map<string, TokenBucket>();

  constructor(
    private readonly settings: RateLimitConfig,
    private readonly clock: MonotonicClock = systemClock,
  ) {}

  get trackedCallers(): number {
    return this.buckets.size;
  }

  tryTake(key: string): TakeDecision {
    const bucket = this.buckets.get(key) ?? this.createBucket();
    this.buckets.delete(key);
    this.buckets.set(key, bucket);
    return bucket.tryTake();
  }

  private createBucket(): TokenBucket {
    this.evictLeastRecentlyUsed();
    return new TokenBucket(this.settings.burst, this.settings.requestsPerSecond, this.clock);
  }

  private evictLeastRecentlyUsed(): void {
    if (this.buckets.size < this.settings.maxTrackedCallers) {
      return;
    }
    for (const leastRecentlyUsed of this.buckets.keys()) {
      this.buckets.delete(leastRecentlyUsed);
      break;
    }
  }
}
