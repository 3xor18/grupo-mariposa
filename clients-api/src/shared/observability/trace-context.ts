import { Injectable } from '@nestjs/common';
import { AsyncLocalStorage } from 'node:async_hooks';
import { randomBytes } from 'node:crypto';
import { IncomingHttpHeaders } from 'node:http';

export const TRACEPARENT_HEADER = 'traceparent';
export const REQUEST_ID_HEADER = 'x-request-id';

const TRACE_ID_BYTES = 16;
const TRACEPARENT_PATTERN = /^[\da-f]{2}-([\da-f]{32})-[\da-f]{16}-[\da-f]{2}$/;
const INVALID_TRACE_ID = /^0+$/;
const REQUEST_ID_PATTERN = /^[\w.-]{1,128}$/;

export interface TraceContext {
  readonly traceId: string;
  readonly requestId: string;
}

export function newTraceId(): string {
  return randomBytes(TRACE_ID_BYTES).toString('hex');
}

function singleHeader(headers: IncomingHttpHeaders, name: string): string | undefined {
  const value = headers[name];
  return typeof value === 'string' ? value : undefined;
}

function traceIdFromTraceparent(traceparent: string | undefined): string | undefined {
  const traceId = TRACEPARENT_PATTERN.exec(traceparent?.toLowerCase() ?? '')?.[1];
  return traceId === undefined || INVALID_TRACE_ID.test(traceId) ? undefined : traceId;
}

function validRequestId(requestId: string | undefined): string | undefined {
  return requestId !== undefined && REQUEST_ID_PATTERN.test(requestId) ? requestId : undefined;
}

export function resolveTraceContext(headers: IncomingHttpHeaders): TraceContext {
  const requestId = validRequestId(singleHeader(headers, REQUEST_ID_HEADER));
  const traceId =
    traceIdFromTraceparent(singleHeader(headers, TRACEPARENT_HEADER)) ?? requestId ?? newTraceId();
  return { traceId, requestId: requestId ?? traceId };
}

@Injectable()
export class TraceContextStore {
  private readonly storage = new AsyncLocalStorage<TraceContext>();

  run<T>(context: TraceContext, callback: () => T): T {
    return this.storage.run(context, callback);
  }

  current(): TraceContext | undefined {
    return this.storage.getStore();
  }

  currentTraceId(): string {
    return this.current()?.traceId ?? newTraceId();
  }
}
