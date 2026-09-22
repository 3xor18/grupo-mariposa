import { NextFunction, Request, RequestHandler, Response } from 'express';
import { REQUEST_ID_HEADER, resolveTraceContext, TraceContextStore } from './trace-context';

export function createTraceContextMiddleware(store: TraceContextStore): RequestHandler {
  return (request: Request, response: Response, next: NextFunction): void => {
    const context = resolveTraceContext(request.headers);
    response.setHeader(REQUEST_ID_HEADER, context.requestId);
    store.run(context, next);
  };
}
