import { ArgumentsHost, Catch, ExceptionFilter, Injectable } from '@nestjs/common';
import { Request, Response } from 'express';
import { InjectPinoLogger, PinoLogger } from 'nestjs-pino';
import { TraceContextStore } from '../observability/trace-context';
import { SERVER_ERROR_STATUS_THRESHOLD } from './error-catalog';
import { describeException } from './exception-to-problem';
import { buildProblemDetails, PROBLEM_CONTENT_TYPE, ProblemDescriptor } from './problem-details';
import { RequestAbortedError } from './request-aborted.error';

export const LOG_MESSAGES = {
  unexpected: 'Unexpected error while handling request',
  serverProblem: 'Request failed with a server error',
  responseUnavailable: 'Response discarded because the client is gone or headers were sent',
} as const;

@Catch()
@Injectable()
export class ProblemDetailsFilter implements ExceptionFilter {
  constructor(
    @InjectPinoLogger(ProblemDetailsFilter.name) private readonly logger: PinoLogger,
    private readonly traceContext: TraceContextStore,
  ) {}

  catch(exception: unknown, host: ArgumentsHost): void {
    const http = host.switchToHttp();
    const request = http.getRequest<Request>();
    const response = http.getResponse<Response>();
    const traceId = this.traceContext.currentTraceId();
    if (exception instanceof RequestAbortedError || response.headersSent) {
      this.logger.info({ traceId, path: request.path }, LOG_MESSAGES.responseUnavailable);
      return;
    }
    const descriptor = describeException(exception);
    this.log(exception, descriptor, traceId);
    const problem = buildProblemDetails(descriptor, {
      instance: request.path,
      traceId,
      timestamp: new Date(),
    });
    response
      .status(descriptor.status)
      .set(descriptor.headers ?? {})
      .type(PROBLEM_CONTENT_TYPE)
      .json(problem);
  }

  private log(exception: unknown, descriptor: ProblemDescriptor, traceId: string): void {
    if (descriptor.unexpected) {
      this.logger.error({ err: exception, traceId }, LOG_MESSAGES.unexpected);
    } else if (descriptor.status >= SERVER_ERROR_STATUS_THRESHOLD) {
      this.logger.warn({ code: descriptor.code, traceId }, LOG_MESSAGES.serverProblem);
    }
  }
}
