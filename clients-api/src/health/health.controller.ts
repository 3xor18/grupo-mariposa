import { Controller, Get, HttpStatus, Res } from '@nestjs/common';
import { ApiOperation, ApiResponse, ApiTags } from '@nestjs/swagger';
import { Response } from 'express';
import { Public } from '../shared/auth/public.decorator';
import { OPERATIONS, RESPONSE_DESCRIPTIONS } from '../shared/constants/openapi.constants';
import { ROUTES } from '../shared/constants/routes.constants';
import { SkipRateLimit } from '../shared/rate-limit/skip-rate-limit.decorator';
import { HealthDto, HealthStatus } from './health.dto';
import { ReadinessState } from './readiness.state';

const HEALTH_DESCRIPTION = RESPONSE_DESCRIPTIONS.health;

@Public()
@SkipRateLimit()
@ApiTags(ROUTES.HEALTH)
@Controller(ROUTES.HEALTH)
export class HealthController {
  constructor(private readonly readiness: ReadinessState) {}

  @Get(ROUTES.LIVENESS)
  @ApiOperation(OPERATIONS.liveness)
  @ApiResponse({ status: HttpStatus.OK, description: HEALTH_DESCRIPTION, type: HealthDto })
  live(): HealthDto {
    return new HealthDto(HealthStatus.UP);
  }

  @Get(ROUTES.READINESS)
  @ApiOperation(OPERATIONS.readiness)
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
