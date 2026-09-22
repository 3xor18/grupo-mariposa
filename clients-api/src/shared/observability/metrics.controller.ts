import { Controller, Get, Res } from '@nestjs/common';
import { ApiExcludeController } from '@nestjs/swagger';
import { Response } from 'express';
import { Public } from '../auth/public.decorator';
import { SkipRateLimit } from '../rate-limit/skip-rate-limit.decorator';
import { HttpMetrics } from './http-metrics';

export const METRICS_ROUTE = 'metrics';

@Public()
@SkipRateLimit()
@ApiExcludeController()
@Controller(METRICS_ROUTE)
export class MetricsController {
  constructor(private readonly metrics: HttpMetrics) {}

  @Get()
  async scrape(@Res({ passthrough: true }) response: Response): Promise<string> {
    response.type(this.metrics.contentType);
    return this.metrics.render();
  }
}
