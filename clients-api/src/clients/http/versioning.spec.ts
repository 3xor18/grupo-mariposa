import { CallHandler, ExecutionContext } from '@nestjs/common';
import { lastValueFrom, of } from 'rxjs';
import { ProblemException } from '../../shared/errors/problem.exception';
import {
  EtagInterceptor,
  formatEtag,
  IF_MATCH_MESSAGE,
  parseIfMatch,
  preconditionOf,
} from './versioning';

function contextWith(headers: Record<string, string>, setHeader = jest.fn()): ExecutionContext {
  return {
    switchToHttp: () => ({
      getRequest: () => ({ headers }),
      getResponse: () => ({ setHeader }),
    }),
  } as unknown as ExecutionContext;
}

describe('If-Match', () => {
  it('should_format_strong_etags', () => {
    expect(formatEtag(3)).toBe('"3"');
  });

  it.each([undefined, '*', ' * '])('should_be_unconditional_for_%p', (header) => {
    expect(parseIfMatch(header)).toBeUndefined();
  });

  it.each([
    ['"3"', [3]],
    ['3', [3]],
    ['"1", "4"', [1, 4]],
    [' "2" ,5 ', [2, 5]],
    ['W/"3"', []],
    ['"abc"', []],
    ['"0"', [0]],
    ['"abc", "7", W/"8"', [7]],
  ])('should_accept_entity_tag_list_%p', (header, versions) => {
    expect(parseIfMatch(header)).toEqual({ acceptedVersions: versions });
  });

  it.each(['', ' ', '"3', '3"', '"3",', ',"3"', 'abc', 'W/3', '"3", *', '"a"b"'])(
    'should_reject_malformed_if_match_%p',
    (header) => {
      expect(() => parseIfMatch(header)).toThrow(ProblemException);
      expect(() => parseIfMatch(header)).toThrow(IF_MATCH_MESSAGE);
    },
  );

  it('should_read_the_precondition_from_the_request', () => {
    expect(preconditionOf(contextWith({ 'if-match': '"5"' }))).toEqual({
      acceptedVersions: [5],
    });
    expect(preconditionOf(contextWith({}))).toBeUndefined();
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
