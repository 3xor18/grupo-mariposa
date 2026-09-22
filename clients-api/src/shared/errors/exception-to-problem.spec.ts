import {
  BadGatewayException,
  BadRequestException,
  HttpException,
  HttpStatus,
  InternalServerErrorException,
  MethodNotAllowedException,
  NotFoundException,
  ServiceUnavailableException,
} from '@nestjs/common';
import { ClientNotFoundError } from '../../clients/domain/client-not-found.error';
import { ErrorCode } from './error-code.enum';
import { describeException, INTERNAL_ERROR_DETAIL } from './exception-to-problem';
import { ProblemException } from './problem.exception';

describe('describeException', () => {
  it('should_keep_code_detail_errors_headers_and_cause_when_problem_exception', () => {
    const cause = new Error('internal');
    const exception = new ProblemException(ErrorCode.RATE_LIMITED, 'slow down', {
      headers: { 'Retry-After': '3' },
      errors: [{ field: 'a', message: 'b' }],
      cause,
    });

    expect(describeException(exception)).toEqual({
      status: 429,
      code: ErrorCode.RATE_LIMITED,
      title: 'Too many requests',
      detail: 'slow down',
      headers: { 'Retry-After': '3' },
      errors: [{ field: 'a', message: 'b' }],
      cause,
      unexpected: false,
    });
  });

  it('should_map_client_not_found_to_404_when_domain_error', () => {
    expect(describeException(new ClientNotFoundError('CLI-1'))).toEqual({
      status: 404,
      code: ErrorCode.CLIENT_NOT_FOUND,
      title: 'Client not found',
      detail: 'Client CLI-1 does not exist',
      unexpected: false,
    });
  });

  it.each([
    [new NotFoundException('Cannot GET /x'), 404, ErrorCode.NOT_FOUND, 'Cannot GET /x'],
    [new BadRequestException('bad'), 400, ErrorCode.BAD_REQUEST, 'bad'],
    [
      new MethodNotAllowedException('POST not allowed'),
      405,
      ErrorCode.METHOD_NOT_ALLOWED,
      'POST not allowed',
    ],
  ])('should_map_known_client_http_exception_%#', (exception, status, code, detail) => {
    expect(describeException(exception)).toMatchObject({
      status,
      code,
      detail,
      unexpected: false,
    });
  });

  it.each([
    [
      new InternalServerErrorException('db password=hunter2'),
      500,
      ErrorCode.INTERNAL_ERROR,
      'An unexpected error occurred',
    ],
    [
      new BadGatewayException('upstream 10.0.0.7 refused'),
      502,
      ErrorCode.BAD_GATEWAY,
      'An upstream dependency returned an invalid response',
    ],
    [
      new ServiceUnavailableException('pool exhausted at host x'),
      503,
      ErrorCode.SERVICE_UNAVAILABLE,
      'The service is temporarily unavailable',
    ],
    [
      new HttpException('gateway timeout internals', HttpStatus.GATEWAY_TIMEOUT),
      504,
      ErrorCode.INTERNAL_ERROR,
      'An unexpected error occurred',
    ],
  ])('should_never_expose_server_exception_message_%#', (exception, status, code, detail) => {
    expect(describeException(exception)).toEqual(
      expect.objectContaining({ status, code, detail, unexpected: true }),
    );
  });

  it('should_keep_status_and_standard_title_when_http_status_is_not_catalogued', () => {
    const exception = new HttpException('too large', HttpStatus.PAYLOAD_TOO_LARGE);

    expect(describeException(exception)).toEqual({
      status: 413,
      code: ErrorCode.BAD_REQUEST,
      title: 'Payload Too Large',
      detail: 'too large',
      unexpected: false,
    });
  });

  it('should_fallback_to_catalog_title_when_status_has_no_standard_reason', () => {
    expect(describeException(new HttpException('custom', 499))).toEqual({
      status: 499,
      code: ErrorCode.BAD_REQUEST,
      title: 'Bad request',
      detail: 'custom',
      unexpected: false,
    });
  });

  it.each([new Error('secret failure'), 'plain string'])(
    'should_hide_details_and_flag_unexpected_when_error_is_unknown_%#',
    (exception) => {
      expect(describeException(exception)).toEqual({
        status: 500,
        code: ErrorCode.INTERNAL_ERROR,
        title: 'Internal server error',
        detail: INTERNAL_ERROR_DETAIL,
        unexpected: true,
      });
    },
  );
});
