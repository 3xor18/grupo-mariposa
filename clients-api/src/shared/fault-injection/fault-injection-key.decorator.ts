import { CustomDecorator, SetMetadata } from '@nestjs/common';

export const FAULT_INJECTION_KEY = 'faultInjection:keyParam';

export const FaultInjectionKey = (routeParam: string): CustomDecorator =>
  SetMetadata(FAULT_INJECTION_KEY, routeParam);
