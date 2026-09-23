import { Module } from '@nestjs/common';
import { GetClientUseCase } from './application/get-client.use-case';
import { ClientsController } from './http/clients.controller';
import { clientRepositoryProvider } from './infrastructure/client-repository.provider';

@Module({
  controllers: [ClientsController],
  providers: [GetClientUseCase, clientRepositoryProvider],
})
export class ClientsModule {}
