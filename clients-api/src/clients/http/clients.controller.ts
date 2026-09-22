import { Controller, Get, HttpStatus, Param } from '@nestjs/common';
import { ApiBearerAuth, ApiOkResponse, ApiOperation, ApiParam, ApiTags } from '@nestjs/swagger';
import { FaultInjectionKey } from '../../shared/fault-injection/fault-injection-key.decorator';
import { ApiProblemResponses } from '../../shared/errors/problem.dto';
import { BEARER_AUTH_SCHEME } from '../../shared/auth/bearer-auth.scheme';
import { GetClientUseCase } from '../application/get-client.use-case';
import { ClientResponse } from './client.response';
import { toClientResponse } from './client-response.mapper';
import {
  CLIENT_ID_EXAMPLE,
  CLIENT_ID_PARAM,
  CLIENT_ID_PATTERN,
  GetClientParams,
} from './get-client.params';

export const CLIENTS_ROUTE = 'clients';

@ApiTags(CLIENTS_ROUTE)
@ApiBearerAuth(BEARER_AUTH_SCHEME)
@Controller(CLIENTS_ROUTE)
export class ClientsController {
  constructor(private readonly getClient: GetClientUseCase) {}

  @Get(`:${CLIENT_ID_PARAM}`)
  @FaultInjectionKey(CLIENT_ID_PARAM)
  @ApiOperation({ operationId: 'getClient', summary: 'Get a client by id' })
  @ApiParam({
    name: CLIENT_ID_PARAM,
    required: true,
    schema: { type: 'string', pattern: CLIENT_ID_PATTERN.source },
    example: CLIENT_ID_EXAMPLE,
  })
  @ApiOkResponse({ description: 'Client found', type: ClientResponse })
  @ApiProblemResponses(
    HttpStatus.BAD_REQUEST,
    HttpStatus.UNAUTHORIZED,
    HttpStatus.FORBIDDEN,
    HttpStatus.NOT_FOUND,
    HttpStatus.TOO_MANY_REQUESTS,
    HttpStatus.INTERNAL_SERVER_ERROR,
    HttpStatus.SERVICE_UNAVAILABLE,
  )
  async findById(@Param() params: GetClientParams): Promise<ClientResponse> {
    return toClientResponse(await this.getClient.execute(params.clientId));
  }
}
