import { Inject, Injectable } from '@nestjs/common';
import { Client, UpdateClientCommand } from '../domain/client';
import { CLIENT_REPOSITORY, ClientRepository } from './client.repository';

@Injectable()
export class UpdateClientUseCase {
  constructor(@Inject(CLIENT_REPOSITORY) private readonly clients: ClientRepository) {}

  execute(command: UpdateClientCommand): Promise<Client> {
    return this.clients.update(command);
  }
}
