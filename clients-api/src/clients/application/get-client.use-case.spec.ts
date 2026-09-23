import { aClient } from '../../../test/support/clients';
import { Client, UpdateClientCommand } from '../domain/client';
import { ClientNotFoundError } from '../domain/client-not-found.error';
import { ClientRepository } from './client.repository';
import { GetClientUseCase } from './get-client.use-case';
import { UpdateClientUseCase } from './update-client.use-case';

describe('GetClientUseCase', () => {
  const repository = {
    findById: jest.fn<Promise<Client | null>, [string]>(),
    update: jest.fn<Promise<Client>, [UpdateClientCommand]>(),
  };
  const useCase = new GetClientUseCase(repository satisfies ClientRepository);

  it('should_return_client_when_repository_finds_it', async () => {
    const client = aClient();
    repository.findById.mockResolvedValueOnce(client);

    await expect(useCase.execute('CLI-1')).resolves.toBe(client);
    expect(repository.findById).toHaveBeenCalledWith('CLI-1');
  });

  it('should_throw_client_not_found_when_repository_returns_null', async () => {
    repository.findById.mockResolvedValueOnce(null);

    await expect(useCase.execute('CLI-2')).rejects.toEqual(new ClientNotFoundError('CLI-2'));
  });

  it('should_propagate_repository_failures_untouched', async () => {
    const failure = new Error('storage offline');
    repository.findById.mockRejectedValueOnce(failure);

    await expect(useCase.execute('CLI-3')).rejects.toBe(failure);
  });
});

describe('UpdateClientUseCase', () => {
  it('should_delegate_the_versioned_update_to_the_repository', async () => {
    const updated = aClient({ version: 2 });
    const repository = {
      findById: jest.fn<Promise<Client | null>, [string]>(),
      update: jest.fn<Promise<Client>, [UpdateClientCommand]>().mockResolvedValue(updated),
    };
    const command = { clientId: 'CLI-1', changes: {}, expectedVersion: 1 };

    await expect(new UpdateClientUseCase(repository).execute(command)).resolves.toBe(updated);
    expect(repository.update).toHaveBeenCalledWith(command);
  });
});
