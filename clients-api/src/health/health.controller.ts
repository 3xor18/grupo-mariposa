import { Controller, Get, HttpStatus, Res } from '@nestjs/common';
import { ApiOperation, ApiResponse, ApiTags } from '@nestjs/swagger';
import { Response } from 'express';
import { Public } from '../shared/auth/public.decorator';
import { SkipRateLimit } from '../shared/rate-limit/skip-rate-limit.decorator';
import { HealthDto, HealthStatus } from './health.dto';
import { ReadinessState } from './readiness.state';

export const HEALTH_ROUTE = 'health';
const HEALTH_DESCRIPTION = 'Health status';

@Public()
@SkipRateLimit()
@ApiTags(HEALTH_ROUTE)
@Controller(HEALTH_ROUTE)
export class HealthController {
  constructor(private readonly readiness: ReadinessState) {}

  @Get('live')
  @ApiOperation({ operationId: 'liveness', summary: 'Liveness probe' })
  @ApiResponse({ status: HttpStatus.OK, description: HEALTH_DESCRIPTION, type: HealthDto })
  live(): HealthDto {
    return new HealthDto(HealthStatus.UP);
  }

  @Get('ready')
  @ApiOperation({ operationId: 'readiness', summary: 'Readiness probe' })
  @ApiResponse({ status: HttpStatus.OK, description: HEALTH_DESCRIPTION, type: HealthDto })
  @ApiResponse({
    status: HttpStatus.SERVICE_UNAVAILABLE,
    description: HEALTH_DESCRIPTION,
    type: HealthDto,
  })
  ready(@Res({ passthrough: true }) response: Response): HealthDto {
    if (this.readiness.isReady()) {
      return new HealthDto(HealthStatus.UP);
    }
    response.status(HttpStatus.SERVICE_UNAVAILABLE);
    return new HealthDto(HealthStatus.DOWN);
  }
}
