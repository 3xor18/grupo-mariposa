import { ArgumentsHost } from '@nestjs/common';
import { PinoLogger } from 'nestjs-pino';
import { ClientNotFoundError } from '../../clients/domain/client-not-found.error';
import { TraceContextStore } from '../observability/trace-context';
import { ErrorCode } from './error-code.enum';
import { PROBLEM_CONTENT_TYPE } from './problem-details';
import { LOG_MESSAGES, ProblemDetailsFilter } from './problem-details.filter';
import { ProblemException } from './problem.exception';
import { RequestAbortedError } from './request-aborted.error';

interface ResponseStub {
  headersSent: boolean;
  status: jest.Mock;
  set: jest.Mock;
  type: jest.Mock;
  json: jest.Mock;
  destroy: jest.Mock;
}

const TRACE = { traceId: 'trace-1', requestId: 'req-1' };

function responseStub(headersSent = false): ResponseStub {
  const response: ResponseStub = {
    headersSent,
    status: jest.fn(),
    set: jest.fn(),
    type: jest.fn(),
    json: jest.fn(),
    destroy: jest.fn(),
  };
  response.status.mockReturnValue(response);
  response.set.mockReturnValue(response);
  response.type.mockReturnValue(response);
  return response;
}

function hostFor(response: ResponseStub): ArgumentsHost {
  return {
    switchToHttp: () => ({
      getRequest: () => ({ path: '/clients/CLI-1' }),
      getResponse: () => response,
    }),
  } as unknown as ArgumentsHost;
}

describe('ProblemDetailsFilter', () => {
  const logger = { error: jest.fn(), warn: jest.fn(), info: jest.fn() };
  const store = new TraceContextStore();
  const filter = new ProblemDetailsFilter(logger as unknown as PinoLogger, store);
  const catchWithTrace = (exception: unknown, response: ResponseStub): void => {
    store.run(TRACE, () => {
      filter.catch(exception, hostFor(response));
    });
  };

  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('should_write_problem_json_with_trace_id_when_domain_error', () => {
    const response = responseStub();

    catchWithTrace(new ClientNotFoundError('CLI-1'), response);

    expect(response.status).toHaveBeenCalledWith(404);
    expect(response.set).toHaveBeenCalledWith({});
    expect(response.type).toHaveBeenCalledWith(PROBLEM_CONTENT_TYPE);
    expect(response.json).toHaveBeenCalledWith({
      type: 'https://contracts.grupomariposa.dev/problems/client-not-found',
      title: 'Client not found',
      status: 404,
      code: ErrorCode.CLIENT_NOT_FOUND,
      detail: 'Client CLI-1 does not exist',
      instance: '/clients/CLI-1',
      traceId: 'trace-1',
      timestamp: expect.stringMatching(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z$/) as string,
    });
    expect(logger.error).not.toHaveBeenCalled();
    expect(logger.warn).not.toHaveBeenCalled();
  });

  it('should_set_problem_headers_when_present', () => {
    const response = responseStub();
    const exception = new ProblemException(ErrorCode.RATE_LIMITED, 'slow', {
      headers: { 'Retry-After': '2' },
    });

    catchWithTrace(exception, response);

    expect(response.set).toHaveBeenCalledWith({ 'Retry-After': '2' });
  });

  it('should_log_error_with_stack_and_hide_message_when_error_is_unexpected', () => {
    const response = responseStub();
    const failure = new Error('database is down');

    catchWithTrace(failure, response);

    expect(response.status).toHaveBeenCalledWith(500);
    expect(response.json).toHaveBeenCalledWith(
      expect.objectContaining({ detail: 'An unexpected error occurred', traceId: 'trace-1' }),
    );
    expect(logger.error).toHaveBeenCalledWith(
      { err: failure, traceId: 'trace-1' },
      LOG_MESSAGES.unexpected,
    );
  });

  it('should_generate_a_w3c_trace_id_when_no_context_is_active', () => {
    const response = responseStub();

    filter.catch(new ClientNotFoundError('CLI-1'), hostFor(response));

    expect(response.json).toHaveBeenCalledWith(
      expect.objectContaining({ traceId: expect.stringMatching(/^[\da-f]{32}$/) as string }),
    );
  });

  it('should_log_warning_with_cause_when_known_problem_is_server_error', () => {
    const cause = new Error('jwks endpoint refused');

    catchWithTrace(
      new ProblemException(ErrorCode.SERVICE_UNAVAILABLE, 'down', { cause }),
      responseStub(),
    );

    expect(logger.warn).toHaveBeenCalledWith(
      { code: ErrorCode.SERVICE_UNAVAILABLE, traceId: 'trace-1', err: cause },
      LOG_MESSAGES.serverProblem,
    );
  });

  it('should_only_log_when_client_closed_the_request', () => {
    const response = responseStub();

    catchWithTrace(new RequestAbortedError(), response);

    expect(response.status).not.toHaveBeenCalled();
    expect(response.destroy).not.toHaveBeenCalled();
    expect(logger.info).toHaveBeenCalledWith(
      { traceId: 'trace-1', path: '/clients/CLI-1' },
      LOG_MESSAGES.clientGone,
    );
  });

  it('should_log_error_and_destroy_connection_when_headers_were_already_sent', () => {
    const response = responseStub(true);
    const failure = new Error('stream broke');

    catchWithTrace(failure, response);

    expect(response.status).not.toHaveBeenCalled();
    expect(response.json).not.toHaveBeenCalled();
    expect(response.destroy).toHaveBeenCalledTimes(1);
    expect(logger.error).toHaveBeenCalledWith(
      { err: failure, traceId: 'trace-1' },
      LOG_MESSAGES.headersAlreadySent,
    );
  });
});
