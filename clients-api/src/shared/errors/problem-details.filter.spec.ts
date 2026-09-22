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
}

function responseStub(headersSent = false): ResponseStub {
  const response: ResponseStub = {
    headersSent,
    status: jest.fn(),
    set: jest.fn(),
    type: jest.fn(),
    json: jest.fn(),
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

  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('should_write_problem_json_with_trace_id_when_domain_error', () => {
    const response = responseStub();

    store.run({ traceId: 'trace-1', requestId: 'req-1' }, () => {
      filter.catch(new ClientNotFoundError('CLI-1'), hostFor(response));
    });

    expect(response.status).toHaveBeenCalledWith(404);
    expect(response.set).toHaveBeenCalledWith({});
    expect(response.type).toHaveBeenCalledWith(PROBLEM_CONTENT_TYPE);
    expect(response.json).toHaveBeenCalledWith(
      expect.objectContaining({
        code: ErrorCode.CLIENT_NOT_FOUND,
        instance: '/clients/CLI-1',
        traceId: 'trace-1',
        timestamp: expect.stringMatching(/^\d{4}-\d{2}-\d{2}T/) as string,
      }),
    );
    expect(logger.error).not.toHaveBeenCalled();
    expect(logger.warn).not.toHaveBeenCalled();
  });

  it('should_set_problem_headers_when_present', () => {
    const response = responseStub();
    const exception = new ProblemException(ErrorCode.RATE_LIMITED, 'slow', {
      headers: { 'Retry-After': '2' },
    });

    filter.catch(exception, hostFor(response));

    expect(response.set).toHaveBeenCalledWith({ 'Retry-After': '2' });
  });

  it('should_log_error_with_stack_and_generate_trace_id_when_error_is_unexpected', () => {
    const response = responseStub();
    const failure = new Error('database is down');

    filter.catch(failure, hostFor(response));

    expect(response.status).toHaveBeenCalledWith(500);
    expect(logger.error).toHaveBeenCalledWith(
      { err: failure, traceId: expect.stringMatching(/^[\da-f]{32}$/) as string },
      LOG_MESSAGES.unexpected,
    );
  });

  it('should_log_warning_when_known_problem_is_server_error', () => {
    filter.catch(
      new ProblemException(ErrorCode.SERVICE_UNAVAILABLE, 'down'),
      hostFor(responseStub()),
    );

    expect(logger.warn).toHaveBeenCalledWith(
      expect.objectContaining({ code: ErrorCode.SERVICE_UNAVAILABLE }),
      LOG_MESSAGES.serverProblem,
    );
  });

  it.each([
    [new RequestAbortedError(), false],
    [new Error('late failure'), true],
  ])('should_not_write_response_when_client_is_gone_or_headers_sent_%#', (error, headersSent) => {
    const response = responseStub(headersSent);

    filter.catch(error, hostFor(response));

    expect(response.status).not.toHaveBeenCalled();
    expect(logger.info).toHaveBeenCalledWith(
      expect.objectContaining({ path: '/clients/CLI-1' }),
      LOG_MESSAGES.responseUnavailable,
    );
  });
});
