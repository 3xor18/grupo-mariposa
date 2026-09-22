import { Inject, Injectable } from '@nestjs/common';
import { Client } from '../domain/client';
import { ClientNotFoundError } from '../domain/client-not-found.error';
import { CLIENT_REPOSITORY, ClientRepository } from './client.repository';

@Injectable()
export class GetClientUseCase {
  constructor(@Inject(CLIENT_REPOSITORY) private readonly clients: ClientRepository) {}

  async execute(clientId: string): Promise<Client> {
    const client = await this.clients.findById(clientId);
    if (client === null) {
      throw new ClientNotFoundError(clientId);
    }
    return client;
  }
}
