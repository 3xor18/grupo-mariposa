import { Injectable } from '@nestjs/common';
import { Counter, exponentialBuckets, Histogram } from 'prom-client';
import { MetricsRegistry } from './metrics-registry';

export const UNMATCHED_ROUTE = 'UNMATCHED';

const LATENCY_BUCKET_START_SECONDS = 0.005;
const LATENCY_BUCKET_FACTOR = 2;
const LATENCY_BUCKET_COUNT = 12;
const LABELS = ['method', 'route', 'status_code'] as const;

export interface HttpObservation {
  readonly method: string;
  readonly route: string;
  readonly statusCode: number;
  readonly durationSeconds: number;
}

@Injectable()
export class HttpMetrics {
  private readonly requests: Counter<(typeof LABELS)[number]>;
  private readonly latency: Histogram<(typeof LABELS)[number]>;

  constructor(metrics: MetricsRegistry) {
    this.requests = new Counter({
      name: 'http_requests_total',
      help: 'Total HTTP requests by method, route and status code',
      labelNames: LABELS,
      registers: [metrics.registry],
    });
    this.latency = new Histogram({
      name: 'http_request_duration_seconds',
      help: 'HTTP request latency in seconds by method, route and status code',
      labelNames: LABELS,
      buckets: exponentialBuckets(
        LATENCY_BUCKET_START_SECONDS,
        LATENCY_BUCKET_FACTOR,
        LATENCY_BUCKET_COUNT,
      ),
      registers: [metrics.registry],
    });
  }

  observe(observation: HttpObservation): void {
    const labels = {
      method: observation.method,
      route: observation.route,
      status_code: String(observation.statusCode),
    };
    this.requests.inc(labels);
    this.latency.observe(labels, observation.durationSeconds);
  }
}
