import { Module } from '@nestjs/common';
import { APP_CONFIG, AppConfig } from '../../config/app-config';
import { FAULT_INJECTOR, FaultInjector } from './fault-injector';

export function createFaultInjector(config: AppConfig): FaultInjector {
  return new FaultInjector(config.faultInjection.rules);
}

@Module({
  providers: [{ provide: FAULT_INJECTOR, useFactory: createFaultInjector, inject: [APP_CONFIG] }],
  exports: [FAULT_INJECTOR],
})
export class FaultInjectionModule {}
