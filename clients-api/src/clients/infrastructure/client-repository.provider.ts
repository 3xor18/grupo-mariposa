import { Provider } from '@nestjs/common';
import { CLIENT_REPOSITORY, ClientRepository } from '../application/client.repository';
import { CLIENT_SEED } from './client.seed';
import { InMemoryClientRepository } from './in-memory-client.repository';

export const clientRepositoryProvider: Provider<ClientRepository> = {
  provide: CLIENT_REPOSITORY,
  useFactory: (): ClientRepository => new InMemoryClientRepository(CLIENT_SEED),
};
