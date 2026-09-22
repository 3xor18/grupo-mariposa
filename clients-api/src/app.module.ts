import { DynamicModule, Module } from '@nestjs/common';
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
import { RateLimitGuard } from './shared/rate-limit/rate-limit.guard';
import { RateLimitModule } from './shared/rate-limit/rate-limit.module';

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
      providers: [
        { provide: APP_PIPE, useFactory: createValidationPipe },
        { provide: APP_FILTER, useClass: ProblemDetailsFilter },
        { provide: APP_GUARD, useClass: RateLimitGuard },
        { provide: APP_GUARD, useClass: JwtAuthGuard },
        { provide: APP_INTERCEPTOR, useClass: FaultInjectionInterceptor },
      ],
    };
  }
}
