import { plainToInstance } from 'class-transformer';
import { validate } from 'class-validator';
import { ClientStatus } from '../domain/client-status.enum';
import { Market } from '../domain/market.enum';
import { Segment } from '../domain/segment.enum';
import { TaxRegime } from '../domain/tax-regime.enum';
import { ClientResponse } from './client.response';
import { toClientResponse } from './client-response.mapper';
import { GetClientParams } from './get-client.params';

describe('toClientResponse', () => {
  it('should_map_domain_client_to_contract_fields', () => {
    const response = toClientResponse({
      id: 'CLI-1',
      name: 'Client one',
      status: ClientStatus.BLOCKED,
      segment: Segment.WHOLESALE,
      taxRegime: TaxRegime.SIMPLIFIED,
      market: Market.MX,
    });

    expect(response).toBeInstanceOf(ClientResponse);
    expect(response).toEqual({
      clientId: 'CLI-1',
      name: 'Client one',
      status: ClientStatus.BLOCKED,
      segment: Segment.WHOLESALE,
      taxRegime: TaxRegime.SIMPLIFIED,
      market: Market.MX,
    });
  });
});

describe('GetClientParams', () => {
  const errorsFor = async (clientId: unknown): Promise<number> =>
    (await validate(plainToInstance(GetClientParams, { clientId }))).length;

  it.each(['CLI-99821', 'CLI-A', `CLI-${'Z'.repeat(20)}`])('should_accept_%s', async (clientId) => {
    await expect(errorsFor(clientId)).resolves.toBe(0);
  });

  it.each(['CLI-', 'cli-1', `CLI-${'Z'.repeat(21)}`, 'CLI-1 ', 'PRD-001', 42])(
    'should_reject_%p',
    async (clientId) => {
      await expect(errorsFor(clientId)).resolves.toBe(1);
    },
  );
});
