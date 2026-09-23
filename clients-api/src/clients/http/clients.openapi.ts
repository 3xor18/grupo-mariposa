import { applyDecorators, HttpStatus } from '@nestjs/common';
import { ApiBody, ApiHeader, ApiOkResponse, ApiOperation, ApiParam } from '@nestjs/swagger';
import {
  OPENAPI_TYPES,
  OPERATIONS,
  RESPONSE_DESCRIPTIONS,
} from '../../shared/constants/openapi.constants';
import { ApiProblemResponses } from '../../shared/errors/problem.dto';
import { ClientResponse } from './client.response';
import { CLIENT_ID_EXAMPLE, CLIENT_ID_PARAM, CLIENT_ID_PATTERN } from './get-client.params';
import { UpdateClientRequest } from './update-client.request';
import { ETAG_HEADER, IF_MATCH_HEADER } from './versioning';

const ETAG_RESPONSE_HEADER = {
  [ETAG_HEADER]: {
    description: RESPONSE_DESCRIPTIONS.etag,
    schema: { type: OPENAPI_TYPES.STRING },
  },
};

const ClientIdParam = (): MethodDecorator =>
  ApiParam({
    name: CLIENT_ID_PARAM,
    schema: { type: OPENAPI_TYPES.STRING, pattern: CLIENT_ID_PATTERN.source },
    example: CLIENT_ID_EXAMPLE,
  });

const VersionedClient = (description: string): MethodDecorator =>
  ApiOkResponse({ description, type: ClientResponse, headers: ETAG_RESPONSE_HEADER });

export const ApiGetClient = (): MethodDecorator =>
  applyDecorators(
    ApiOperation(OPERATIONS.getClient),
    ClientIdParam(),
    VersionedClient(RESPONSE_DESCRIPTIONS.clientFound),
    ApiProblemResponses(
      HttpStatus.BAD_REQUEST,
      HttpStatus.UNAUTHORIZED,
      HttpStatus.FORBIDDEN,
      HttpStatus.NOT_FOUND,
      HttpStatus.TOO_MANY_REQUESTS,
      HttpStatus.INTERNAL_SERVER_ERROR,
      HttpStatus.BAD_GATEWAY,
      HttpStatus.SERVICE_UNAVAILABLE,
    ),
  );

export const ApiUpdateClient = (): MethodDecorator =>
  applyDecorators(
    ApiOperation(OPERATIONS.updateClient),
    ClientIdParam(),
    ApiHeader({
      name: IF_MATCH_HEADER,
      required: false,
      description: RESPONSE_DESCRIPTIONS.ifMatch,
    }),
    ApiBody({ type: UpdateClientRequest }),
    VersionedClient(RESPONSE_DESCRIPTIONS.clientUpdated),
    ApiProblemResponses(
      HttpStatus.BAD_REQUEST,
      HttpStatus.UNAUTHORIZED,
      HttpStatus.FORBIDDEN,
      HttpStatus.NOT_FOUND,
      HttpStatus.PRECONDITION_FAILED,
      HttpStatus.TOO_MANY_REQUESTS,
      HttpStatus.INTERNAL_SERVER_ERROR,
      HttpStatus.SERVICE_UNAVAILABLE,
    ),
  );
