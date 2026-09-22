import { ErrorCode } from './error-code.enum';
import { FieldError } from './problem.exception';

export const PROBLEM_CONTENT_TYPE = 'application/problem+json';
export const PROBLEM_TYPE_BASE_URI = 'https://contracts.grupomariposa.dev/problems/';

export interface ProblemDescriptor {
  readonly status: number;
  readonly code: ErrorCode;
  readonly title: string;
  readonly detail: string;
  readonly errors?: readonly FieldError[];
  readonly headers?: Readonly<Record<string, string>>;
  readonly unexpected: boolean;
}

export interface ProblemContext {
  readonly instance: string;
  readonly traceId: string;
  readonly timestamp: Date;
}

export interface ProblemDetails {
  readonly type: string;
  readonly title: string;
  readonly status: number;
  readonly code: ErrorCode;
  readonly detail: string;
  readonly instance: string;
  readonly traceId: string;
  readonly timestamp: string;
  readonly errors?: readonly FieldError[];
}

export function problemTypeFor(code: ErrorCode): string {
  return `${PROBLEM_TYPE_BASE_URI}${code.toLowerCase().replaceAll('_', '-')}`;
}

export function buildProblemDetails(
  descriptor: ProblemDescriptor,
  context: ProblemContext,
): ProblemDetails {
  const problem: ProblemDetails = {
    type: problemTypeFor(descriptor.code),
    title: descriptor.title,
    status: descriptor.status,
    code: descriptor.code,
    detail: descriptor.detail,
    instance: context.instance,
    traceId: context.traceId,
    timestamp: context.timestamp.toISOString(),
  };
  return descriptor.errors === undefined ? problem : { ...problem, errors: descriptor.errors };
}
