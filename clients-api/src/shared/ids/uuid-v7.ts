import { randomBytes } from 'node:crypto';

export type IdGenerator = () => string;

export const ID_GENERATOR = Symbol('ID_GENERATOR');

const HEX_RADIX = 16;
const HEX_ENCODING = 'hex';
const TIMESTAMP_HEX_LENGTH = 12;
const RANDOM_BYTES = 10;
const VERSION_NIBBLE = '7';
const VARIANT_INDEX = 16;
const VARIANT_MASK = 0b0011;
const VARIANT_BITS = 0b1000;
const GROUP_LENGTHS = Object.freeze({ time: 8, mid: 4, version: 4, variant: 4, node: 12 });
const GROUP_SEPARATOR = '-';

function withVariant(hex: string): string {
  const current = Number.parseInt(hex.charAt(VARIANT_INDEX), HEX_RADIX);
  const nibble = ((current & VARIANT_MASK) | VARIANT_BITS).toString(HEX_RADIX);
  return `${hex.slice(0, VARIANT_INDEX)}${nibble}${hex.slice(VARIANT_INDEX + 1)}`;
}

function grouped(hex: string): string {
  let offset = 0;
  return Object.values(GROUP_LENGTHS)
    .map((length) => {
      const group = hex.slice(offset, offset + length);
      offset += length;
      return group;
    })
    .join(GROUP_SEPARATOR);
}

export function uuidV7(
  nowMs: number = Date.now(),
  random: Buffer = randomBytes(RANDOM_BYTES),
): string {
  const timestamp = nowMs.toString(HEX_RADIX).padStart(TIMESTAMP_HEX_LENGTH, '0');
  const entropy = random.toString(HEX_ENCODING);
  return grouped(withVariant(`${timestamp}${VERSION_NIBBLE}${entropy.slice(1)}`));
}

export const uuidV7Generator: IdGenerator = () => uuidV7();
