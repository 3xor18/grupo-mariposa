import { NextFunction, Request, RequestHandler, Response } from 'express';
import { HttpMetrics, UNMATCHED_ROUTE } from './http-metrics';

const NANOSECONDS_PER_SECOND = 1e9;
const WILDCARD = '*';

interface MatchedRoute {
  readonly path: string;
}

function isMatchedRoute(route: unknown): route is MatchedRoute {
  return (
    typeof route === 'object' &&
    route !== null &&
    'path' in route &&
    typeof route.path === 'string' &&
    !route.path.includes(WILDCARD)
  );
}

export function routeLabelOf(request: Request): string {
  const route: unknown = request.route;
  return isMatchedRoute(route) ? `${request.baseUrl}${route.path}` : UNMATCHED_ROUTE;
}

export function createHttpMetricsMiddleware(metrics: HttpMetrics): RequestHandler {
  return (request: Request, response: Response, next: NextFunction): void => {
    const startedAt = process.hrtime.bigint();
    response.once('finish', () => {
      metrics.observe({
        method: request.method,
        route: routeLabelOf(request),
        statusCode: response.statusCode,
        durationSeconds: Number(process.hrtime.bigint() - startedAt) / NANOSECONDS_PER_SECOND,
      });
    });
    next();
  };
}
