import { NextFunction, Request, RequestHandler, Response } from 'express';
import { HTTP_HEADERS } from '../constants/http.constants';
import { resolveTraceContext, TraceContextStore } from './trace-context';

export function createTraceContextMiddleware(store: TraceContextStore): RequestHandler {
  return (request: Request, response: Response, next: NextFunction): void => {
    const context = resolveTraceContext(request.headers);
    response.setHeader(HTTP_HEADERS.REQUEST_ID, context.requestId);
    store.run(context, next);
  };
}
