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
import { VersionPrecondition } from '../domain/client';

export const IF_MATCH_HEADER = 'if-match';
export const ETAG_HEADER = 'ETag';
export const ANY_VERSION = '*';
export const IF_MATCH_MESSAGE = 'must be * or a comma separated list of entity tags such as "3"';

const TAG_SEPARATOR = ',';
const STRONG_TAG = /^"([^"]*)"$/;
const WEAK_TAG = /^W\/"[^"]*"$/;
const VERSION_NUMBER = /^\d+$/;
const QUOTE = '"';

export function formatEtag(version: number): string {
  return `${QUOTE}${String(version)}${QUOTE}`;
}

function malformedIfMatch(): ProblemException {
  return new ProblemException(ErrorCode.VALIDATION_ERROR, IF_MATCH_MESSAGE, {
    errors: [{ field: IF_MATCH_HEADER, message: IF_MATCH_MESSAGE }],
  });
}

function versionsOfTag(tag: string): number[] {
  if (VERSION_NUMBER.test(tag)) {
    return [Number(tag)];
  }
  if (WEAK_TAG.test(tag)) {
    return [];
  }
  const opaque = STRONG_TAG.exec(tag)?.[1];
  if (opaque === undefined) {
    throw malformedIfMatch();
  }
  return VERSION_NUMBER.test(opaque) ? [Number(opaque)] : [];
}

export function parseIfMatch(header: string | undefined): VersionPrecondition | undefined {
  if (header === undefined || header.trim() === ANY_VERSION) {
    return undefined;
  }
  const acceptedVersions = header
    .split(TAG_SEPARATOR)
    .map((tag) => tag.trim())
    .flatMap(versionsOfTag);
  return { acceptedVersions };
}

export function preconditionOf(context: ExecutionContext): VersionPrecondition | undefined {
  return parseIfMatch(context.switchToHttp().getRequest<Request>().headers[IF_MATCH_HEADER]);
}

export const IfMatch = createParamDecorator((_data: unknown, context: ExecutionContext) =>
  preconditionOf(context),
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
