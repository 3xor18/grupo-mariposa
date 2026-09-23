import { Client } from '../../src/clients/domain/client';
import { ClientStatus } from '../../src/clients/domain/client-status.enum';
import { Segment } from '../../src/clients/domain/segment.enum';
import { TaxRegime } from '../../src/clients/domain/tax-regime.enum';

export function aClient(overrides: Partial<Client> = {}): Client {
  return {
    id: 'CLI-1',
    name: 'Test client',
    status: ClientStatus.ACTIVE,
    segment: Segment.RETAIL,
    taxRegime: TaxRegime.EXEMPT,
    market: 'PE',
    version: 1,
    ...overrides,
  };
}
