import { MarketCatalog } from '../../shared/markets/market-catalog';
import { Client, INITIAL_CLIENT_VERSION } from '../domain/client';
import { ClientStatus } from '../domain/client-status.enum';
import { Segment } from '../domain/segment.enum';
import { TaxRegime } from '../domain/tax-regime.enum';

const { ACTIVE, BLOCKED } = ClientStatus;
const { WHOLESALE, RETAIL } = Segment;
const { GENERAL, SIMPLIFIED, EXEMPT } = TaxRegime;

function client(
  id: string,
  name: string,
  market: string,
  segment: Segment,
  taxRegime: TaxRegime,
  status: ClientStatus,
): Client {
  return Object.freeze({
    id,
    name,
    market,
    segment,
    taxRegime,
    status,
    version: INITIAL_CLIENT_VERSION,
  });
}

export const MASTER_DATA_CLIENTS: readonly Client[] = Object.freeze([
  client('CLI-99821', 'Distribuidora Central', 'MX', WHOLESALE, GENERAL, ACTIVE),
  client('CLI-10002', 'Abarrotes La Esperanza', 'MX', RETAIL, SIMPLIFIED, ACTIVE),
  client('CLI-10003', 'Comercializadora del Norte', 'MX', WHOLESALE, EXEMPT, ACTIVE),
  client('CLI-20001', 'Mayorista Andino', 'CO', WHOLESALE, GENERAL, ACTIVE),
  client('CLI-20002', 'Tienda El Porvenir', 'CO', RETAIL, GENERAL, BLOCKED),
  client('CLI-30001', 'Distribuidora Lima Norte', 'PE', WHOLESALE, GENERAL, ACTIVE),
  client('CLI-30002', 'Bodega San Martín', 'PE', RETAIL, EXEMPT, ACTIVE),
  client('CLI-50001', 'Distribuidora Santiago', 'CL', WHOLESALE, GENERAL, ACTIVE),
  client('CLI-50002', 'Almacén Valparaíso', 'CL', RETAIL, GENERAL, ACTIVE),
  client('CLI-60001', 'Mayorista Guayaquil', 'EC', WHOLESALE, GENERAL, ACTIVE),
  client('CLI-60002', 'Tienda Quito', 'EC', RETAIL, EXEMPT, ACTIVE),
]);

export const DEMO_RESILIENCE_CLIENTS: readonly Client[] = Object.freeze([
  client('CLI-40001', 'Distribuidora Resiliente', 'MX', WHOLESALE, GENERAL, ACTIVE),
  client('CLI-40002', 'Comercial Intermitente', 'MX', RETAIL, GENERAL, ACTIVE),
]);

export const DEMO_CACHE_CLIENTS: readonly Client[] = Object.freeze([
  client('CLI-70001', 'Cliente demo caché', 'MX', WHOLESALE, GENERAL, ACTIVE),
]);

export const CLIENT_SEED: readonly Client[] = Object.freeze([
  ...MASTER_DATA_CLIENTS,
  ...DEMO_RESILIENCE_CLIENTS,
  ...DEMO_CACHE_CLIENTS,
]);

export function seedForCatalog(catalog: MarketCatalog): readonly Client[] {
  return CLIENT_SEED.filter((seeded) => catalog.includes(seeded.market));
}
