import { applyDecorators, HttpStatus } from '@nestjs/common';
import {
  ApiExtraModels,
  ApiProperty,
  ApiPropertyOptional,
  ApiResponse,
  getSchemaPath,
} from '@nestjs/swagger';
import { PROBLEM_CONTENT_TYPE } from './problem-details';

export const PROBLEM_RESPONSE_DESCRIPTION = 'Error following RFC 9457';

export class FieldErrorDto {
  @ApiProperty()
  readonly field!: string;

  @ApiProperty()
  readonly message!: string;
}

export class ProblemDto {
  @ApiProperty({ format: 'uri-reference' })
  readonly type!: string;

  @ApiProperty()
  readonly title!: string;

  @ApiProperty({ minimum: 400, maximum: 599 })
  readonly status!: number;

  @ApiProperty({ description: 'Stable machine readable code' })
  readonly code!: string;

  @ApiProperty()
  readonly detail!: string;

  @ApiProperty()
  readonly instance!: string;

  @ApiProperty()
  readonly traceId!: string;

  @ApiProperty({ format: 'date-time' })
  readonly timestamp!: string;

  @ApiPropertyOptional({ type: [FieldErrorDto] })
  readonly errors?: FieldErrorDto[];
}

export function ApiProblemResponses(...statuses: HttpStatus[]): MethodDecorator & ClassDecorator {
  return applyDecorators(
    ApiExtraModels(ProblemDto),
    ...statuses.map((status) =>
      ApiResponse({
        status,
        description: PROBLEM_RESPONSE_DESCRIPTION,
        content: { [PROBLEM_CONTENT_TYPE]: { schema: { $ref: getSchemaPath(ProblemDto) } } },
      }),
    ),
  );
}
