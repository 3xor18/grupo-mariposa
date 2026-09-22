import { ApiProperty, ApiSchema } from '@nestjs/swagger';

export const HEALTH_SCHEMA_NAME = 'Health';

export enum HealthStatus {
  UP = 'UP',
  DOWN = 'DOWN',
}

@ApiSchema({ name: HEALTH_SCHEMA_NAME })
export class HealthDto {
  @ApiProperty({ enum: HealthStatus })
  readonly status: HealthStatus;

  constructor(status: HealthStatus) {
    this.status = status;
  }
}
