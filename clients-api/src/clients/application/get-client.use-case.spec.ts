import { Client } from '../domain/client';
import { ClientNotFoundError } from '../domain/client-not-found.error';
import { ClientStatus } from '../domain/client-status.enum';
import { Market } from '../domain/market.enum';
import { Segment } from '../domain/segment.enum';
import { TaxRegime } from '../domain/tax-regime.enum';
import { ClientRepository } from './client.repository';
import { GetClientUseCase } from './get-client.use-case';

const CLIENT: Client = {
  id: 'CLI-1',
  name: 'Test client',
  status: ClientStatus.ACTIVE,
  segment: Segment.RETAIL,
  taxRegime: TaxRegime.EXEMPT,
  market: Market.PE,
};

describe('GetClientUseCase', () => {
  const repository = { findById: jest.fn<Promise<Client | null>, [string]>() };
  const useCase = new GetClientUseCase(repository satisfies ClientRepository);

  it('should_return_client_when_repository_finds_it', async () => {
    repository.findById.mockResolvedValueOnce(CLIENT);

    await expect(useCase.execute('CLI-1')).resolves.toBe(CLIENT);
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
