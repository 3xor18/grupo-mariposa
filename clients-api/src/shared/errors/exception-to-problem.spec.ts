import {
  BadRequestException,
  HttpException,
  HttpStatus,
  InternalServerErrorException,
  NotFoundException,
} from '@nestjs/common';
import { ClientNotFoundError } from '../../clients/domain/client-not-found.error';
import { ErrorCode } from './error-code.enum';
import { describeException, INTERNAL_ERROR_DETAIL } from './exception-to-problem';
import { ProblemException } from './problem.exception';

describe('describeException', () => {
  it('should_keep_code_detail_errors_and_headers_when_problem_exception', () => {
    const exception = new ProblemException(ErrorCode.RATE_LIMITED, 'slow down', {
      headers: { 'Retry-After': '3' },
      errors: [{ field: 'a', message: 'b' }],
    });

    expect(describeException(exception)).toEqual({
      status: 429,
      code: ErrorCode.RATE_LIMITED,
      title: 'Too many requests',
      detail: 'slow down',
      headers: { 'Retry-After': '3' },
      errors: [{ field: 'a', message: 'b' }],
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
    [new NotFoundException('Cannot GET /x'), 404, ErrorCode.NOT_FOUND],
    [new BadRequestException('bad'), 400, ErrorCode.BAD_REQUEST],
  ])('should_map_known_http_exception_%#', (exception, status, code) => {
    expect(describeException(exception)).toMatchObject({ status, code, unexpected: false });
  });

  it('should_keep_status_and_standard_title_when_http_status_is_not_catalogued', () => {
    const exception = new HttpException('gone', HttpStatus.METHOD_NOT_ALLOWED);

    expect(describeException(exception)).toEqual({
      status: 405,
      code: ErrorCode.BAD_REQUEST,
      title: 'Method Not Allowed',
      detail: 'gone',
      unexpected: false,
    });
  });

  it('should_fallback_to_catalog_title_when_status_has_no_standard_reason', () => {
    expect(describeException(new HttpException('custom', 499))).toMatchObject({
      status: 499,
      title: 'Bad request',
    });
  });

  it('should_flag_unexpected_when_http_exception_is_server_error', () => {
    expect(describeException(new InternalServerErrorException('boom'))).toMatchObject({
      status: 500,
      code: ErrorCode.INTERNAL_ERROR,
      unexpected: true,
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
