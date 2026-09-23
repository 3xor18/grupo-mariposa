import { Module } from '@nestjs/common';
import { APP_CONFIG, AppConfig } from '../../config/app-config';
import { KeyedRateLimiter } from './keyed-rate-limiter';
import { CLIENT_ADDRESS_RATE_LIMITER, PRINCIPAL_RATE_LIMITER } from './rate-limit.guard';

export function createRateLimiter(config: AppConfig): KeyedRateLimiter {
  return new KeyedRateLimiter(config.rateLimit);
}

@Module({
  providers: [
    { provide: CLIENT_ADDRESS_RATE_LIMITER, useFactory: createRateLimiter, inject: [APP_CONFIG] },
    { provide: PRINCIPAL_RATE_LIMITER, useFactory: createRateLimiter, inject: [APP_CONFIG] },
  ],
  exports: [CLIENT_ADDRESS_RATE_LIMITER, PRINCIPAL_RATE_LIMITER],
})
export class RateLimitModule {}
