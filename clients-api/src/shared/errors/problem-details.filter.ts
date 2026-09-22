import { ArgumentsHost, Catch, ExceptionFilter, Injectable } from '@nestjs/common';
import { Request, Response } from 'express';
import { InjectPinoLogger, PinoLogger } from 'nestjs-pino';
import { TraceContextStore } from '../observability/trace-context';
import { SERVER_ERROR_STATUS_THRESHOLD } from './error-catalog';
import { describeException } from './exception-to-problem';
import { buildProblemDetails, PROBLEM_CONTENT_TYPE, ProblemDescriptor } from './problem-details';
import { RequestAbortedError } from './request-aborted.error';

export const LOG_MESSAGES = Object.freeze({
  unexpected: 'Unexpected error while handling request',
  serverProblem: 'Request failed with a server error',
  clientGone: 'Response discarded because the client closed the request',
  headersAlreadySent: 'Error after response headers were sent, connection destroyed',
});

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
    if (this.handledWithoutBody(exception, request, response, traceId)) {
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

  private handledWithoutBody(
    exception: unknown,
    request: Request,
    response: Response,
    traceId: string,
  ): boolean {
    if (exception instanceof RequestAbortedError) {
      this.logger.info({ traceId, path: request.path }, LOG_MESSAGES.clientGone);
      return true;
    }
    if (response.headersSent) {
      this.logger.error({ err: exception, traceId }, LOG_MESSAGES.headersAlreadySent);
      response.destroy();
      return true;
    }
    return false;
  }

  private log(exception: unknown, descriptor: ProblemDescriptor, traceId: string): void {
    if (descriptor.unexpected) {
      this.logger.error({ err: exception, traceId }, LOG_MESSAGES.unexpected);
    } else if (descriptor.status >= SERVER_ERROR_STATUS_THRESHOLD) {
      this.logger.warn(
        { code: descriptor.code, traceId, err: descriptor.cause },
        LOG_MESSAGES.serverProblem,
      );
    }
  }
}
