import { Module } from '@nestjs/common';
import { APP_CONFIG, AppConfig } from '../../config/app-config';
import { RATE_LIMITER } from './rate-limit.guard';
import { TokenBucket } from './token-bucket';

export function createRateLimiter(config: AppConfig): TokenBucket {
  return new TokenBucket(config.rateLimit.burst, config.rateLimit.requestsPerSecond);
}

@Module({
  providers: [{ provide: RATE_LIMITER, useFactory: createRateLimiter, inject: [APP_CONFIG] }],
  exports: [RATE_LIMITER],
})
export class RateLimitModule {}
