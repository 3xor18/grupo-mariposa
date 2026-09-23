import { aClient } from '../../../test/support/clients';
import { createContractValidators } from '../../../test/support/contracts';
import { clientChangedEvent } from './client-changed.event';

describe('clientChangedEvent', () => {
  it('should_carry_the_full_client_state_and_match_the_event_contract', () => {
    const event = clientChangedEvent(
      aClient({ id: 'CLI-50001', market: 'CL', version: 7 }),
      '0199b2c4-0000-7000-8000-000000000001',
      new Date('2026-09-23T10:00:00.000Z'),
    );
    const validate = createContractValidators().clientChanged;

    expect(event).toEqual({
      eventId: '0199b2c4-0000-7000-8000-000000000001',
      occurredAt: '2026-09-23T10:00:00.000Z',
      clientId: 'CLI-50001',
      version: 7,
      status: 'ACTIVE',
      segment: 'RETAIL',
      taxRegime: 'EXEMPT',
      market: 'CL',
    });
    expect(validate(event)).toBe(true);
    expect(validate.errors ?? []).toEqual([]);
  });
});
