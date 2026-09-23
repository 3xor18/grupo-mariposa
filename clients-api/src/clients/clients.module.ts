import { DynamicModule, Module } from '@nestjs/common';
import { StorageDriver } from '../config/app-config';
import { GetClientUseCase } from './application/get-client.use-case';
import { UpdateClientUseCase } from './application/update-client.use-case';
import { ClientsController } from './http/clients.controller';
import { storageProvidersFor } from './infrastructure/client-repository.provider';

@Module({})
export class ClientsModule {
  static forStorage(driver: StorageDriver): DynamicModule {
    return {
      module: ClientsModule,
      controllers: [ClientsController],
      providers: [GetClientUseCase, UpdateClientUseCase, ...storageProvidersFor(driver)],
    };
  }
}
