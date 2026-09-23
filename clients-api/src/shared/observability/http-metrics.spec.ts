import { Request, Response } from 'express';
import { EventEmitter } from 'node:events';
import { HttpMetrics, UNMATCHED_ROUTE } from './http-metrics';
import {
  CLIENT_CLOSED_REQUEST_STATUS,
  createHttpMetricsMiddleware,
  routeLabelOf,
} from './http-metrics.middleware';

describe('HttpMetrics', () => {
  it('should_count_and_time_requests_by_method_route_and_status', async () => {
    const metrics = new HttpMetrics();

    metrics.observe({ method: 'GET', route: '/x', statusCode: 200, durationSeconds: 0.01 });
    const output = await metrics.render();

    expect(metrics.contentType).toContain('text/plain');
    expect(output).toContain('http_requests_total{method="GET",route="/x",status_code="200"} 1');
    expect(output).toContain(
      'http_request_duration_seconds_count{method="GET",route="/x",status_code="200"} 1',
    );
  });
});

describe('routeLabelOf', () => {
  it.each([
    [{ baseUrl: '', route: { path: '/clients/:clientId' } }, '/clients/:clientId'],
    [{ baseUrl: '/api', route: { path: '/x' } }, '/api/x'],
    [{ baseUrl: '', route: { path: '{/*splat}' } }, UNMATCHED_ROUTE],
    [{ baseUrl: '', route: { path: 1 } }, UNMATCHED_ROUTE],
    [{ baseUrl: '', route: {} }, UNMATCHED_ROUTE],
    [{ baseUrl: '', route: null }, UNMATCHED_ROUTE],
    [{ baseUrl: '' }, UNMATCHED_ROUTE],
  ])('should_label_route_%#', (request, expected) => {
    expect(routeLabelOf(request as unknown as Request)).toBe(expected);
  });
});

describe('createHttpMetricsMiddleware', () => {
  const observeWith = (writableFinished: boolean, events: readonly string[]): jest.Mock => {
    const metrics = { observe: jest.fn() };
    const response = Object.assign(new EventEmitter(), { statusCode: 404, writableFinished });
    const request = { method: 'GET', baseUrl: '', route: { path: '/a' } };
    const next = jest.fn();

    createHttpMetricsMiddleware(metrics as unknown as HttpMetrics)(
      request as unknown as Request,
      response as unknown as Response,
      next,
    );
    for (const event of events) {
      response.emit(event);
    }

    expect(next).toHaveBeenCalledTimes(1);
    return metrics.observe;
  };

  it('should_observe_once_when_response_finishes_and_then_closes', () => {
    const observe = observeWith(true, ['finish', 'close']);

    expect(observe).toHaveBeenCalledTimes(1);
    expect(observe).toHaveBeenCalledWith({
      method: 'GET',
      route: '/a',
      statusCode: 404,
      durationSeconds: expect.any(Number) as number,
    });
  });

  it('should_observe_aborted_requests_as_client_closed_when_connection_closes_early', () => {
    const observe = observeWith(false, ['close']);

    expect(observe).toHaveBeenCalledTimes(1);
    expect(observe).toHaveBeenCalledWith(
      expect.objectContaining({ statusCode: CLIENT_CLOSED_REQUEST_STATUS }),
    );
  });
});
