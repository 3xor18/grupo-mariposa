import { NestExpressApplication } from '@nestjs/platform-express';
import { Logger } from 'nestjs-pino';
import { createHttpMetricsMiddleware } from './shared/observability/http-metrics.middleware';
import { HttpMetrics } from './shared/observability/http-metrics';
import { TraceContextStore } from './shared/observability/trace-context';
import { createTraceContextMiddleware } from './shared/observability/trace-context.middleware';
import { setupSwagger } from './swagger';

const POWERED_BY_SETTING = 'x-powered-by';

export function configureApp(app: NestExpressApplication): NestExpressApplication {
  app.useLogger(app.get(Logger));
  app.disable(POWERED_BY_SETTING);
  app.use(createTraceContextMiddleware(app.get(TraceContextStore)));
  app.use(createHttpMetricsMiddleware(app.get(HttpMetrics)));
  setupSwagger(app);
  return app;
}
