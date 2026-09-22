import { CustomDecorator, SetMetadata } from '@nestjs/common';

export const SKIP_RATE_LIMIT_KEY = 'rateLimit:skip';

export const SkipRateLimit = (): CustomDecorator => SetMetadata(SKIP_RATE_LIMIT_KEY, true);
