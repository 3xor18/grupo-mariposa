import { uuidV7, uuidV7Generator } from './uuid-v7';

const UUID_V7 = /^[\da-f]{8}-[\da-f]{4}-7[\da-f]{3}-[89ab][\da-f]{3}-[\da-f]{12}$/;

describe('uuidV7', () => {
  it('should_encode_the_timestamp_version_and_variant', () => {
    const id = uuidV7(0x0199b2c4d5e6, Buffer.from('ffffffffffffffffffff', 'hex'));

    expect(id).toBe('0199b2c4-d5e6-7fff-bfff-ffffffffffff');
  });

  it('should_force_the_rfc_variant_bits', () => {
    expect(uuidV7(1, Buffer.alloc(10))).toBe('00000000-0001-7000-8000-000000000000');
  });

  it('should_generate_sortable_unique_ids_by_default', () => {
    const first = uuidV7Generator();
    const second = uuidV7(Date.now() + 1);

    expect(first).toMatch(UUID_V7);
    expect(second).toMatch(UUID_V7);
    expect(first).not.toBe(second);
    expect(first < second).toBe(true);
  });
});
