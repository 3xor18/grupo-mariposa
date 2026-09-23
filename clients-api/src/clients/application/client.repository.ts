import { Client } from '../domain/client';

export const CLIENT_REPOSITORY = Symbol('CLIENT_REPOSITORY');

export interface ClientRepository {
  findById(id: string): Promise<Client | null>;
}
