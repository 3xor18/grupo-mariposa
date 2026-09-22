import { ApiProperty } from '@nestjs/swagger';
import { IsString, Matches } from 'class-validator';

export const CLIENT_ID_PARAM = 'clientId';
export const CLIENT_ID_PATTERN = /^CLI-[A-Z0-9]{1,20}$/;
export const CLIENT_ID_EXAMPLE = 'CLI-99821';
export const CLIENT_ID_FORMAT_MESSAGE = `${CLIENT_ID_PARAM} must match ${CLIENT_ID_PATTERN.source}`;

export class GetClientParams {
  @ApiProperty({ pattern: CLIENT_ID_PATTERN.source, example: CLIENT_ID_EXAMPLE })
  @IsString()
  @Matches(CLIENT_ID_PATTERN, { message: CLIENT_ID_FORMAT_MESSAGE })
  readonly clientId!: string;
}
