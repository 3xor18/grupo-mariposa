import { CallHandler, ExecutionContext } from '@nestjs/common';
import { lastValueFrom, of } from 'rxjs';
import { ProblemException } from '../../shared/errors/problem.exception';
import {
  EtagInterceptor,
  expectedVersionOf,
  formatEtag,
  IF_MATCH_MESSAGE,
  parseIfMatch,
} from './versioning';

function contextWith(headers: Record<string, string>, setHeader = jest.fn()): ExecutionContext {
  return {
    switchToHttp: () => ({
      getRequest: () => ({ headers }),
      getResponse: () => ({ setHeader }),
    }),
  } as unknown as ExecutionContext;
}

describe('versioning', () => {
  it('should_format_strong_etags', () => {
    expect(formatEtag(3)).toBe('"3"');
  });

  it.each([
    [undefined, undefined],
    ['*', undefined],
    [' * ', undefined],
    ['"3"', 3],
    ['3', 3],
    ['W/"12"', 12],
  ])('should_parse_if_match_%p', (header, expected) => {
    expect(parseIfMatch(header)).toBe(expected);
  });

  it.each(['"0"', 'abc', '"3', '-1', '"3", "4"', ''])(
    'should_reject_malformed_if_match_%p',
    (header) => {
      expect(() => parseIfMatch(header)).toThrow(ProblemException);
      expect(() => parseIfMatch(header)).toThrow(IF_MATCH_MESSAGE);
    },
  );

  it('should_read_the_expected_version_from_the_request', () => {
    expect(expectedVersionOf(contextWith({ 'if-match': '"5"' }))).toBe(5);
    expect(expectedVersionOf(contextWith({}))).toBeUndefined();
  });

  it('should_set_etag_from_the_returned_version', async () => {
    const setHeader = jest.fn();
    const next: CallHandler<{ version: number }> = { handle: () => of({ version: 9 }) };

    const body = await lastValueFrom(
      new EtagInterceptor().intercept(contextWith({}, setHeader), next),
    );

    expect(body).toEqual({ version: 9 });
    expect(setHeader).toHaveBeenCalledWith('ETag', '"9"');
  });
});
