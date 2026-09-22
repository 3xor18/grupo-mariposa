import { ClientStatus } from './client-status.enum';
import { Market } from './market.enum';
import { Segment } from './segment.enum';
import { TaxRegime } from './tax-regime.enum';

export interface Client {
  readonly id: string;
  readonly name: string;
  readonly status: ClientStatus;
  readonly segment: Segment;
  readonly taxRegime: TaxRegime;
  readonly market: Market;
}
