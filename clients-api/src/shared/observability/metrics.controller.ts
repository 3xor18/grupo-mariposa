import { Controller, Get, Res } from '@nestjs/common';
import { ApiExcludeController } from '@nestjs/swagger';
import { Response } from 'express';
import { Public } from '../auth/public.decorator';
import { ROUTES } from '../constants/routes.constants';
import { SkipRateLimit } from '../rate-limit/skip-rate-limit.decorator';
import { MetricsRegistry } from './metrics-registry';

@Public()
@SkipRateLimit()
@ApiExcludeController()
@Controller(ROUTES.METRICS)
export class MetricsController {
  constructor(private readonly metrics: MetricsRegistry) {}

  @Get()
  async scrape(@Res({ passthrough: true }) response: Response): Promise<string> {
    response.type(this.metrics.contentType);
    return this.metrics.render();
  }
}
