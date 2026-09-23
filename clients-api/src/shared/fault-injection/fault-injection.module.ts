import { Global, Module } from '@nestjs/common';
import { APP_CONFIG, AppConfig, FaultInjectionConfig } from '../../config/app-config';
import { FAULT_INJECTION_CONFIG, FAULT_INJECTOR, PENDING_HOLDS } from './fault-injection.tokens';
import { FaultInjector } from './fault-injector';
import { PendingHolds } from './pending-holds';

export function faultInjectionConfigOf(config: AppConfig): FaultInjectionConfig {
  return config.faultInjection;
}

export function createFaultInjector(config: FaultInjectionConfig): FaultInjector {
  return new FaultInjector(config.enabled ? config.rules : []);
}

@Global()
@Module({
  providers: [
    { provide: FAULT_INJECTION_CONFIG, useFactory: faultInjectionConfigOf, inject: [APP_CONFIG] },
    { provide: FAULT_INJECTOR, useFactory: createFaultInjector, inject: [FAULT_INJECTION_CONFIG] },
    { provide: PENDING_HOLDS, useFactory: (): PendingHolds => new PendingHolds() },
  ],
  exports: [FAULT_INJECTION_CONFIG, FAULT_INJECTOR, PENDING_HOLDS],
})
export class FaultInjectionModule {}
