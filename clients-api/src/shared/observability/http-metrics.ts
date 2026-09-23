import { Injectable } from '@nestjs/common';
import {
  collectDefaultMetrics,
  Counter,
  exponentialBuckets,
  Histogram,
  Registry,
} from 'prom-client';

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
  private readonly registry = new Registry();
  private readonly requests = new Counter({
    name: 'http_requests_total',
    help: 'Total HTTP requests by method, route and status code',
    labelNames: LABELS,
    registers: [this.registry],
  });
  private readonly latency = new Histogram({
    name: 'http_request_duration_seconds',
    help: 'HTTP request latency in seconds by method, route and status code',
    labelNames: LABELS,
    buckets: exponentialBuckets(
      LATENCY_BUCKET_START_SECONDS,
      LATENCY_BUCKET_FACTOR,
      LATENCY_BUCKET_COUNT,
    ),
    registers: [this.registry],
  });

  constructor() {
    collectDefaultMetrics({ register: this.registry });
  }

  get contentType(): string {
    return this.registry.contentType;
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

  render(): Promise<string> {
    return this.registry.metrics();
  }
}
