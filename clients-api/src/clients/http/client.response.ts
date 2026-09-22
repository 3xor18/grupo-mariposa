import { ApiProperty } from '@nestjs/swagger';
import { ClientStatus } from '../domain/client-status.enum';
import { Market } from '../domain/market.enum';
import { Segment } from '../domain/segment.enum';
import { TaxRegime } from '../domain/tax-regime.enum';

export class ClientResponse {
  @ApiProperty()
  readonly clientId: string;

  @ApiProperty()
  readonly name: string;

  @ApiProperty({ enum: ClientStatus, enumName: 'ClientStatus' })
  readonly status: ClientStatus;

  @ApiProperty({ enum: Segment, enumName: 'Segment' })
  readonly segment: Segment;

  @ApiProperty({ enum: TaxRegime, enumName: 'TaxRegime' })
  readonly taxRegime: TaxRegime;

  @ApiProperty({ enum: Market, enumName: 'Market' })
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
