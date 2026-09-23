import { Injectable, PipeTransform } from '@nestjs/common';
import { ApiPropertyOptional, ApiSchema } from '@nestjs/swagger';
import { IsEnum, IsOptional } from 'class-validator';
import { ErrorCode } from '../../shared/errors/error-code.enum';
import { ProblemException } from '../../shared/errors/problem.exception';
import { ClientChanges } from '../domain/client';
import { ClientStatus } from '../domain/client-status.enum';
import { Segment } from '../domain/segment.enum';
import { TaxRegime } from '../domain/tax-regime.enum';

export const UPDATE_CLIENT_SCHEMA_NAME = 'UpdateClient';
export const REQUEST_BODY_FIELD = 'body';
export const EMPTY_CHANGES_MESSAGE = 'at least one of status, segment or taxRegime is required';

@ApiSchema({ name: UPDATE_CLIENT_SCHEMA_NAME })
export class UpdateClientRequest {
  @ApiPropertyOptional({ enum: ClientStatus })
  @IsOptional()
  @IsEnum(ClientStatus)
  readonly status?: ClientStatus;

  @ApiPropertyOptional({ enum: Segment })
  @IsOptional()
  @IsEnum(Segment)
  readonly segment?: Segment;

  @ApiPropertyOptional({ enum: TaxRegime })
  @IsOptional()
  @IsEnum(TaxRegime)
  readonly taxRegime?: TaxRegime;
}

export function toClientChanges(request: UpdateClientRequest): ClientChanges {
  return {
    ...(request.status === undefined ? {} : { status: request.status }),
    ...(request.segment === undefined ? {} : { segment: request.segment }),
    ...(request.taxRegime === undefined ? {} : { taxRegime: request.taxRegime }),
  };
}

@Injectable()
export class RequireChangesPipe implements PipeTransform<UpdateClientRequest> {
  transform(request: UpdateClientRequest): UpdateClientRequest {
    if (Object.keys(toClientChanges(request)).length === 0) {
      throw new ProblemException(ErrorCode.VALIDATION_ERROR, EMPTY_CHANGES_MESSAGE, {
        errors: [{ field: REQUEST_BODY_FIELD, message: EMPTY_CHANGES_MESSAGE }],
      });
    }
    return request;
  }
}
