import {
  CallHandler,
  createParamDecorator,
  ExecutionContext,
  Injectable,
  NestInterceptor,
} from '@nestjs/common';
import { Request, Response } from 'express';
import { map, Observable } from 'rxjs';
import { ErrorCode } from '../../shared/errors/error-code.enum';
import { ProblemException } from '../../shared/errors/problem.exception';

export const IF_MATCH_HEADER = 'if-match';
export const ETAG_HEADER = 'ETag';
export const ANY_VERSION = '*';
export const IF_MATCH_MESSAGE = 'must be a version number such as "3" or *';

const VERSION_PATTERN = /^(?:W\/)?("?)([1-9]\d*)\1$/;
const VERSION_GROUP = 2;
const QUOTE = '"';

export function formatEtag(version: number): string {
  return `${QUOTE}${String(version)}${QUOTE}`;
}

export function parseIfMatch(header: string | undefined): number | undefined {
  const value = header?.trim();
  if (value === undefined || value === ANY_VERSION) {
    return undefined;
  }
  const version = VERSION_PATTERN.exec(value)?.[VERSION_GROUP];
  if (version === undefined) {
    throw new ProblemException(ErrorCode.VALIDATION_ERROR, IF_MATCH_MESSAGE, {
      errors: [{ field: IF_MATCH_HEADER, message: IF_MATCH_MESSAGE }],
    });
  }
  return Number(version);
}

export function expectedVersionOf(context: ExecutionContext): number | undefined {
  return parseIfMatch(context.switchToHttp().getRequest<Request>().headers[IF_MATCH_HEADER]);
}

export const ExpectedVersion = createParamDecorator((_data: unknown, context: ExecutionContext) =>
  expectedVersionOf(context),
);

interface Versioned {
  readonly version: number;
}

@Injectable()
export class EtagInterceptor implements NestInterceptor {
  intercept(context: ExecutionContext, next: CallHandler<Versioned>): Observable<Versioned> {
    const response = context.switchToHttp().getResponse<Response>();
    return next.handle().pipe(
      map((body) => {
        response.setHeader(ETAG_HEADER, formatEtag(body.version));
        return body;
      }),
    );
  }
}
