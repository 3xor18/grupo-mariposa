import { ApiProperty, ApiSchema } from '@nestjs/swagger';
import { ClientStatus } from '../domain/client-status.enum';
import { Market } from '../domain/market.enum';
import { Segment } from '../domain/segment.enum';
import { TaxRegime } from '../domain/tax-regime.enum';

export const CLIENT_SCHEMA_NAME = 'Client';

@ApiSchema({ name: CLIENT_SCHEMA_NAME })
export class ClientResponse {
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

  @ApiProperty({ enum: Market })
  readonly market: Market;

  constructor(fields: ClientResponse) {
    this.clientId = fields.clientId;
    this.name = fields.name;
    this.status = fields.status;
    this.segment = fields.segment;
    this.taxRegime = fields.taxRegime;
    this.market = fields.market;
  }
}
