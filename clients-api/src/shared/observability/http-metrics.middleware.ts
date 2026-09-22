import { NextFunction, Request, RequestHandler, Response } from 'express';
import { RESPONSE_EVENTS } from '../constants/http.constants';
import { HttpMetrics, UNMATCHED_ROUTE } from './http-metrics';

const NANOSECONDS_PER_SECOND = 1e9;
const WILDCARD = '*';
export const CLIENT_CLOSED_REQUEST_STATUS = 499;

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

function statusCodeOf(response: Response): number {
  return response.writableFinished ? response.statusCode : CLIENT_CLOSED_REQUEST_STATUS;
}

export function createHttpMetricsMiddleware(metrics: HttpMetrics): RequestHandler {
  return (request: Request, response: Response, next: NextFunction): void => {
    const startedAt = process.hrtime.bigint();
    let observed = false;
    const observe = (): void => {
      if (observed) {
        return;
      }
      observed = true;
      metrics.observe({
        method: request.method,
        route: routeLabelOf(request),
        statusCode: statusCodeOf(response),
        durationSeconds: Number(process.hrtime.bigint() - startedAt) / NANOSECONDS_PER_SECOND,
      });
    };
    response.once(RESPONSE_EVENTS.FINISH, observe);
    response.once(RESPONSE_EVENTS.CLOSE, observe);
    next();
  };
}
