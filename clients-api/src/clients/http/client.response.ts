import { ApiProperty, ApiSchema } from '@nestjs/swagger';
import { MARKET_CODE_PATTERN } from '../../shared/markets/market-catalog';
import { ClientStatus } from '../domain/client-status.enum';
import { Segment } from '../domain/segment.enum';
import { TaxRegime } from '../domain/tax-regime.enum';

export const CLIENT_SCHEMA_NAME = 'Client';
const MINIMUM_VERSION = 1;

@ApiSchema({ name: CLIENT_SCHEMA_NAME })
export class ClientResponse {
  @ApiProperty({ minimum: MINIMUM_VERSION, required: false })
  readonly version: number;

  @ApiProperty()
  readonly clientId: string;

  @ApiProperty()
  readonly name: string;

  @ApiProperty({ enum: ClientStatus })
  readonly status: ClientStatus;

  @ApiProperty({ enum: Segment })
  readonly segment: Segment;

  @ApiProperty({ enum: TaxRegime })
  readonly taxRegime: TaxRegime;

  @ApiProperty({ pattern: MARKET_CODE_PATTERN.source })
  readonly market: string;

  constructor(fields: ClientResponse) {
    this.version = fields.version;
    this.clientId = fields.clientId;
    this.name = fields.name;
    this.status = fields.status;
    this.segment = fields.segment;
    this.taxRegime = fields.taxRegime;
    this.market = fields.market;
  }
}
