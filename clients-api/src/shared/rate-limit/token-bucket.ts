export const MILLISECONDS_PER_SECOND = 1000;

export type MonotonicClock = () => number;

export interface TakeDecision {
  readonly allowed: boolean;
  readonly retryAfterSeconds: number;
}

export const systemClock: MonotonicClock = () => performance.now();

export class TokenBucket {
  private tokens: number;
  private lastRefillAt: number;

  constructor(
    private readonly capacity: number,
    private readonly refillPerSecond: number,
    private readonly clock: MonotonicClock = systemClock,
  ) {
    this.tokens = capacity;
    this.lastRefillAt = clock();
  }

  tryTake(): TakeDecision {
    this.refill();
    if (this.tokens >= 1) {
      this.tokens -= 1;
      return { allowed: true, retryAfterSeconds: 0 };
    }
    return { allowed: false, retryAfterSeconds: this.secondsUntilNextToken() };
  }

  private refill(): void {
    const now = this.clock();
    const elapsedSeconds = (now - this.lastRefillAt) / MILLISECONDS_PER_SECOND;
    this.tokens = Math.min(this.capacity, this.tokens + elapsedSeconds * this.refillPerSecond);
    this.lastRefillAt = now;
  }

  private secondsUntilNextToken(): number {
    return Math.max(1, Math.ceil((1 - this.tokens) / this.refillPerSecond));
  }
}
