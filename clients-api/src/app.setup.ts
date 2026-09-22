import { NestExpressApplication } from '@nestjs/platform-express';
import { Logger } from 'nestjs-pino';
import { APP_CONFIG, AppConfig } from './config/app-config';
import { createHttpMetricsMiddleware } from './shared/observability/http-metrics.middleware';
import { HttpMetrics } from './shared/observability/http-metrics';
import { TraceContextStore } from './shared/observability/trace-context';
import { createTraceContextMiddleware } from './shared/observability/trace-context.middleware';
import { setupSwagger } from './swagger';

export const EXPRESS_SETTINGS = Object.freeze({
  POWERED_BY: 'x-powered-by',
  TRUST_PROXY: 'trust proxy',
});

export function configureApp(app: NestExpressApplication): NestExpressApplication {
  const config = app.get<AppConfig>(APP_CONFIG);
  app.useLogger(app.get(Logger));
  app.disable(EXPRESS_SETTINGS.POWERED_BY);
  app.set(EXPRESS_SETTINGS.TRUST_PROXY, config.http.trustProxy);
  app.use(createTraceContextMiddleware(app.get(TraceContextStore)));
  app.use(createHttpMetricsMiddleware(app.get(HttpMetrics)));
  if (config.http.apiDocsEnabled) {
    setupSwagger(app);
  }
  return app;
}
