import { DynamicModule, Module, Provider } from '@nestjs/common';
import { APP_FILTER, APP_GUARD, APP_INTERCEPTOR, APP_PIPE } from '@nestjs/core';
import { LoggerModule } from 'nestjs-pino';
import { ClientsModule } from './clients/clients.module';
import { APP_CONFIG, AppConfig } from './config/app-config';
import { ConfigModule } from './config/config.module';
import { HealthModule } from './health/health.module';
import { AuthModule } from './shared/auth/auth.module';
import { JwtAuthGuard } from './shared/auth/jwt-auth.guard';
import { ProblemDetailsFilter } from './shared/errors/problem-details.filter';
import { createValidationPipe } from './shared/errors/validation';
import { FaultInjectionInterceptor } from './shared/fault-injection/fault-injection.interceptor';
import { FaultInjectionModule } from './shared/fault-injection/fault-injection.module';
import { buildLoggerParams } from './shared/observability/logger.params';
import { ObservabilityModule } from './shared/observability/observability.module';
import { TraceContextStore } from './shared/observability/trace-context';
import {
  ClientAddressRateLimitGuard,
  PrincipalRateLimitGuard,
} from './shared/rate-limit/rate-limit.guard';
import { RateLimitModule } from './shared/rate-limit/rate-limit.module';

const REQUEST_PIPELINE: readonly Provider[] = Object.freeze([
  { provide: APP_PIPE, useFactory: createValidationPipe },
  { provide: APP_FILTER, useClass: ProblemDetailsFilter },
  { provide: APP_GUARD, useClass: ClientAddressRateLimitGuard },
  { provide: APP_GUARD, useClass: JwtAuthGuard },
  { provide: APP_GUARD, useClass: PrincipalRateLimitGuard },
  { provide: APP_INTERCEPTOR, useClass: FaultInjectionInterceptor },
]);

@Module({})
export class AppModule {
  static forRoot(config: AppConfig): DynamicModule {
    return {
      module: AppModule,
      imports: [
        ConfigModule.forRoot(config),
        ObservabilityModule,
        LoggerModule.forRootAsync({
          inject: [APP_CONFIG, TraceContextStore],
          useFactory: buildLoggerParams,
        }),
        AuthModule,
        RateLimitModule,
        FaultInjectionModule,
        HealthModule,
        ClientsModule,
      ],
      providers: [...REQUEST_PIPELINE],
    };
  }
}
