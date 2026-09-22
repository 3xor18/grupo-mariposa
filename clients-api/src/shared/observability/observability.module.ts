import { Global, Module } from '@nestjs/common';
import { HttpMetrics } from './http-metrics';
import { MetricsController } from './metrics.controller';
import { TraceContextStore } from './trace-context';

@Global()
@Module({
  controllers: [MetricsController],
  providers: [TraceContextStore, HttpMetrics],
  exports: [TraceContextStore, HttpMetrics],
})
export class ObservabilityModule {}
