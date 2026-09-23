import { Body, Controller, Get, Param, Patch, UseInterceptors } from '@nestjs/common';
import { ApiBearerAuth, ApiTags } from '@nestjs/swagger';
import { AccessRole, RequireRole } from '../../shared/auth/access-role';
import { BEARER_AUTH_SCHEME } from '../../shared/auth/bearer-auth.scheme';
import { ROUTES } from '../../shared/constants/routes.constants';
import { FaultInjectionKey } from '../../shared/fault-injection/fault-injection-key.decorator';
import { GetClientUseCase } from '../application/get-client.use-case';
import { UpdateClientUseCase } from '../application/update-client.use-case';
import { VersionPrecondition } from '../domain/client';
import { ClientResponse } from './client.response';
import { toClientResponse } from './client-response.mapper';
import { ApiGetClient, ApiUpdateClient } from './clients.openapi';
import { CLIENT_ID_PARAM, GetClientParams } from './get-client.params';
import { RequireChangesPipe, toClientChanges, UpdateClientRequest } from './update-client.request';
import { EtagInterceptor, IfMatch } from './versioning';

const CLIENT_ID_ROUTE = `:${CLIENT_ID_PARAM}`;

@ApiTags(ROUTES.CLIENTS)
@ApiBearerAuth(BEARER_AUTH_SCHEME)
@UseInterceptors(EtagInterceptor)
@Controller(ROUTES.CLIENTS)
export class ClientsController {
  constructor(
    private readonly getClient: GetClientUseCase,
    private readonly updateClient: UpdateClientUseCase,
  ) {}

  @Get(CLIENT_ID_ROUTE)
  @FaultInjectionKey(CLIENT_ID_PARAM)
  @ApiGetClient()
  async findById(@Param() params: GetClientParams): Promise<ClientResponse> {
    return toClientResponse(await this.getClient.execute(params.clientId));
  }

  @Patch(CLIENT_ID_ROUTE)
  @RequireRole(AccessRole.ADMIN)
  @ApiUpdateClient()
  async update(
    @Param() params: GetClientParams,
    @Body(RequireChangesPipe) request: UpdateClientRequest,
    @IfMatch() precondition: VersionPrecondition | undefined,
  ): Promise<ClientResponse> {
    const command = {
      clientId: params.clientId,
      changes: toClientChanges(request),
      ...(precondition === undefined ? {} : { precondition }),
    };
    return toClientResponse(await this.updateClient.execute(command));
  }
}
