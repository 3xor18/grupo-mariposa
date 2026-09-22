import { NextFunction, Request, Response } from 'express';
import { resolveTraceContext, TraceContextStore } from './trace-context';
import { createTraceContextMiddleware } from './trace-context.middleware';

const TRACE_ID = '4bf92f3577b34da6a3ce929d0e0e4736';
const TRACEPARENT = `00-${TRACE_ID}-00f067aa0ba902b7-01`;

describe('resolveTraceContext', () => {
  it('should_use_traceparent_trace_id_and_request_id_when_both_are_valid', () => {
    expect(resolveTraceContext({ traceparent: TRACEPARENT, 'x-request-id': 'abc-1' })).toEqual({
      traceId: TRACE_ID,
      requestId: 'abc-1',
    });
  });

  it('should_accept_uppercase_traceparent', () => {
    expect(resolveTraceContext({ traceparent: TRACEPARENT.toUpperCase() }).traceId).toBe(TRACE_ID);
  });

  it('should_generate_w3c_trace_id_and_keep_request_id_when_traceparent_is_absent', () => {
    const context = resolveTraceContext({ 'x-request-id': 'abc-1' });

    expect(context.traceId).toMatch(/^[\da-f]{32}$/);
    expect(context.requestId).toBe('abc-1');
  });

  it.each([
    { traceparent: 'garbage' },
    { traceparent: `00-${'0'.repeat(32)}-00f067aa0ba902b7-01` },
    { 'x-request-id': 'bad id with spaces' },
    { 'x-request-id': 'a'.repeat(129) },
    { 'x-request-id': ['a', 'b'] },
    {},
  ])('should_generate_new_ids_when_headers_are_invalid_%#', (headers) => {
    const context = resolveTraceContext(headers);

    expect(context.traceId).toMatch(/^[\da-f]{32}$/);
    expect(context.requestId).toBe(context.traceId);
  });
});

describe('TraceContextStore', () => {
  it('should_expose_context_only_inside_run', () => {
    const store = new TraceContextStore();
    const context = { traceId: 't', requestId: 'r' };

    expect(store.run(context, () => store.current())).toBe(context);
    expect(store.run(context, () => store.currentTraceId())).toBe('t');
    expect(store.current()).toBeUndefined();
    expect(store.currentTraceId()).toMatch(/^[\da-f]{32}$/);
  });
});

describe('createTraceContextMiddleware', () => {
  it('should_echo_request_id_and_run_next_inside_context', () => {
    const store = new TraceContextStore();
    const setHeader = jest.fn();
    let seen: string | undefined;
    const next: NextFunction = () => {
      seen = store.current()?.traceId;
    };

    createTraceContextMiddleware(store)(
      { headers: { traceparent: TRACEPARENT } } as unknown as Request,
      { setHeader } as unknown as Response,
      next,
    );

    expect(setHeader).toHaveBeenCalledWith('x-request-id', TRACE_ID);
    expect(seen).toBe(TRACE_ID);
  });
});
