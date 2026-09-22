import { ApiProperty } from '@nestjs/swagger';

export enum HealthStatus {
  UP = 'UP',
  DOWN = 'DOWN',
}

export class HealthDto {
  @ApiProperty({ enum: HealthStatus, enumName: 'HealthStatus' })
  readonly status: HealthStatus;

  constructor(status: HealthStatus) {
    this.status = status;
  }
}
