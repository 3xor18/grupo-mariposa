import { Global, Module } from '@nestjs/common';
import { HttpMetrics } from './http-metrics';
import { MetricsController } from './metrics.controller';
import { MetricsRegistry } from './metrics-registry';
import { TraceContextStore } from './trace-context';

@Global()
@Module({
  controllers: [MetricsController],
  providers: [TraceContextStore, MetricsRegistry, HttpMetrics],
  exports: [TraceContextStore, MetricsRegistry, HttpMetrics],
})
export class ObservabilityModule {}
